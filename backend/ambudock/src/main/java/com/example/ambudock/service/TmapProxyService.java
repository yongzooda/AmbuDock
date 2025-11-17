package com.example.ambudock.service;

import com.example.ambudock.dto.tmap.TmapDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * TMAP OpenAPI 호출/정규화 서비스
 *
 * - 후보 병원 K(≤10)에 대해 TMAP OpenAPI를 직접 호출하여
 *   · 차량 ETA/경로거리 (/tmap/routes)
 *   · 도보 ETA/경로거리 (/tmap/routes/pedestrian)
 *   · 직선 거리 (/tmap/routes/distance)
 *   를 계산한 뒤, 내부 표준 DTO(TmapDtos.RouteResponse)로 변환한다.
 *
 * - 실패(타임아웃/오류/빈 응답) 시, 하버사인 기반 추정치(source="ESTIMATE")로 폴백
 * - 결과는 @Cacheable("eta")로 120초 캐시 (TTL은 CacheConfig에서 설정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TmapProxyService {

    @Value("${tmap.proxy.base-url}")
    private String tmapBaseUrl;

    @Value("${tmap.proxy.timeout-ms}")
    private int tmapTimeoutMs;

    @Value("${tmap.app-key}")
    private String tmapAppKey;

    private final ObjectMapper objectMapper;

    /* ---------------- WebClient 재사용 ---------------- */

    /** 마지막 사용한 baseUrl */
    private volatile String lastBaseUrl = null;
    /** 재사용할 WebClient (baseUrl 변경 시 교체) */
    private volatile WebClient cachedClient = null;

    /**
     * baseUrl을 읽어 WebClient를 제공.
     * - 동일 URL이면 재사용, 바뀌면 재생성
     */
    private WebClient client() {
        String baseUrl = tmapBaseUrl;
        WebClient local = cachedClient;
        if (local != null && baseUrl.equals(lastBaseUrl)) {
            return local;
        }
        synchronized (this) {
            if (cachedClient == null || !baseUrl.equals(lastBaseUrl)) {
                cachedClient = WebClient.builder()
                        .baseUrl(baseUrl)
                        .build();
                lastBaseUrl = baseUrl;
                log.info("TMAP WebClient reinitialized. baseUrl={}", baseUrl);
            }
            return cachedClient;
        }
    }

    /* ---------------- Public API ---------------- */

    /**
     * 후보 K 병원에 대한 차량/도보 ETA와 거리 요청
     * - 캐시 키: origin + candidates + 옵션(car/walk)을 문자열로 묶어 MD5 (동일 입력 120s 재사용)
     * - 후보별로 TMAP OpenAPI를 직접 호출해서 Result를 만들고,
     *   모든 후보가 실패하면 전체 ESTIMATE 폴백을 사용.
     */
    @Cacheable(
            cacheNames = "eta",
            key = "T(org.springframework.util.DigestUtils).md5DigestAsHex((" +
                    "#req.origin + '|' + " +
                    "#req.candidates.![hpid + ':' + lat + ',' + lon].toString() + '|' + " +
                    "(#req.options != null ? (#req.options.car + ',' + #req.options.walk) : 'true,true')" +
                    ").bytes)"
    )
    public TmapDtos.RouteResponse routeForCandidates(TmapDtos.RouteRequest req, String clientIp) {
        // 1) 기본 방어
        if (req == null || req.getOrigin() == null || req.getOrigin().indexOf(',') < 0) {
            throw new IllegalArgumentException("origin format must be 'lat,lon'");
        }

        if (req.getCandidates() == null || req.getCandidates().isEmpty()) {
            return TmapDtos.RouteResponse.builder()
                    .items(List.of())
                    .durationMs(0L)
                    .source("ESTIMATE")
                    .cached(0)
                    .build();
        }

        int timeoutMs = Math.max(500, tmapTimeoutMs);
        double[] origin = parseLatLon(req.getOrigin());
        double oLat = origin[0];
        double oLon = origin[1];

        long startNano = System.nanoTime();
        List<TmapDtos.Result> items = new ArrayList<>();
        boolean anyTmapSuccess = false;

        try {
            for (TmapDtos.RouteRequest.Candidate cand : req.getCandidates()) {
                try {
                    double dLat = getDouble(cand.getLat());
                    double dLon = getDouble(cand.getLon());

                    // 직선거리: /tmap/routes/distance 우선, 실패시 하버사인
                    Double lineKm = callDistanceApi(oLon, oLat, dLon, dLat, timeoutMs);
                    if (lineKm == null) {
                        lineKm = haversineKm(oLat, oLon, dLat, dLon);
                    }

                    boolean wantCar = (req.getOptions() == null) || Boolean.TRUE.equals(req.getOptions().isCar());
                    boolean wantWalk = (req.getOptions() == null) || Boolean.TRUE.equals(req.getOptions().isWalk());

                    Double etaCarMin = null;
                    Double routeCarKm = null;
                    if (wantCar) {
                        TmapLegMetrics car = callCarRoute(oLon, oLat, dLon, dLat, timeoutMs);
                        if (car != null) {
                            etaCarMin = car.etaMin;
                            routeCarKm = car.distanceKm;
                        }
                    }

                    Double etaWalkMin = null;
                    Double routeWalkKm = null;
                    if (wantWalk) {
                        TmapLegMetrics walk = callPedestrianRoute(oLon, oLat, dLon, dLat, timeoutMs);
                        if (walk != null) {
                            etaWalkMin = walk.etaMin;
                            routeWalkKm = walk.distanceKm;
                        }
                    }

                    // 최소한 직선거리라도 구했으면 TMAP 성공으로 간주(ETA 실패 시 lineKm만 채워짐)
                    if (lineKm != null) {
                        anyTmapSuccess = true;

                        // 경로거리 없으면 직선거리로 대체
                        if (routeCarKm == null && etaCarMin != null) {
                            routeCarKm = lineKm;
                        }
                        if (routeWalkKm == null && etaWalkMin != null) {
                            routeWalkKm = lineKm;
                        }

                        items.add(TmapDtos.Result.builder()
                                .hpid(cand.getHpid())
                                .eta_car_min(etaCarMin)
                                .eta_walk_min(etaWalkMin)
                                .distance_line_km(lineKm)
                                .route_car_km(routeCarKm)
                                .route_walk_km(routeWalkKm)
                                .source("TMAP")
                                .build());
                    } else {
                        // 이 후보만 ESTIMATE 폴백
                        log.warn("TMAP route+distance all failed for hpid={}. Fallback to ESTIMATE for this candidate.", cand.getHpid());
                        items.add(estimateForSingleCandidate(req, oLat, oLon, cand));
                    }
                } catch (Exception e) {
                    log.warn("TMAP candidate error (fallback to ESTIMATE). hpid={}, cause={}", cand.getHpid(), e.toString());
                    items.add(estimateForSingleCandidate(req, oLat, oLon, cand));
                }
            }
        } catch (Exception e) {
            // 후보 전체 처리 중에 치명적 에러 → 전부 ESTIMATE로 폴백
            log.warn("TMAP proxy fatal exception, fallback all to ESTIMATE: {}", e.toString());
            return estimateFromOriginAndCandidates(req);
        }

        long durationMs = (System.nanoTime() - startNano) / 1_000_000L;
        String source = anyTmapSuccess ? "TMAP" : "ESTIMATE";

        // 혹시 모든 후보가 ESTIMATE로만 채워졌다면, 한 번 더 전체 폴백 로직으로 정합성 맞춰줌
        if (!anyTmapSuccess) {
            return estimateFromOriginAndCandidates(req);
        }

        return TmapDtos.RouteResponse.builder()
                .items(items)
                .durationMs(durationMs)
                .source(source)
                .cached(0)
                .build();
    }

    /* ---------------- TMAP OpenAPI 호출 ---------------- */

    /**
     * /tmap/routes/distance?version=1
     * 직선거리(m)를 받아서 km로 변환. 실패 시 null.
     */
    private Double callDistanceApi(double startLon, double startLat,
                                   double endLon, double endLat,
                                   int timeoutMs) {
        try {
            String json = client().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tmap/routes/distance")
                            .queryParam("version", 1)
                            .queryParam("startX", startLon)
                            .queryParam("startY", startLat)
                            .queryParam("endX", endLon)
                            .queryParam("endY", endLat)
                            .queryParam("reqCoordType", "WGS84GEO")
                            .build())
                    .header("appKey", tmapAppKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorResume(e -> {
                        log.warn("TMAP /routes/distance error: {}", e.toString());
                        return Mono.empty();
                    })
                    .block();

            if (json == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode distanceInfo = root.get("distanceInfo");
            if (distanceInfo != null && distanceInfo.get("distance") != null) {
                double meters = distanceInfo.get("distance").asDouble();
                return meters / 1000.0;
            }
        } catch (Exception e) {
            log.warn("Failed to parse TMAP distance response: {}", e.toString());
        }
        return null;
    }

    /**
     * /tmap/routes?version=1
     * 자동차 경로 안내 → totalDistance(m), totalTime(sec) 를 km/분 단위로 반환. 실패 시 null.
     */
    private TmapLegMetrics callCarRoute(double startLon, double startLat,
                                        double endLon, double endLat,
                                        int timeoutMs) {
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("startX", startLon);
            body.put("startY", startLat);
            body.put("endX", endLon);
            body.put("endY", endLat);
            body.put("reqCoordType", "WGS84GEO");
            body.put("resCoordType", "WGS84GEO");
            body.put("searchOption", 0); // 추천경로
            body.put("trafficInfo", "N");

            String json = client().post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tmap/routes")
                            .queryParam("version", 1)
                            .build())
                    .header("appKey", tmapAppKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorResume(e -> {
                        log.warn("TMAP car route error: {}", e.toString());
                        return Mono.empty();
                    })
                    .block();

            if (json == null) {
                return null;
            }

            return parseRouteMetrics(json);
        } catch (Exception e) {
            log.warn("Failed to call/parse TMAP car route: {}", e.toString());
            return null;
        }
    }

    /**
     * /tmap/routes/pedestrian?version=1
     * 보행자 경로 안내 → totalDistance(m), totalTime(sec) 를 km/분 단위로 반환. 실패 시 null.
     */
    private TmapLegMetrics callPedestrianRoute(double startLon, double startLat,
                                               double endLon, double endLat,
                                               int timeoutMs) {
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("startX", startLon);
            body.put("startY", startLat);
            body.put("endX", endLon);
            body.put("endY", endLat);
            body.put("reqCoordType", "WGS84GEO");
            body.put("resCoordType", "WGS84GEO");
            body.put("searchOption", "0");
            body.put("sort", "index");

            String json = client().post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tmap/routes/pedestrian")
                            .queryParam("version", 1)
                            .build())
                    .header("appKey", tmapAppKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorResume(e -> {
                        log.warn("TMAP pedestrian route error: {}", e.toString());
                        return Mono.empty();
                    })
                    .block();

            if (json == null) {
                return null;
            }

            return parseRouteMetrics(json);
        } catch (Exception e) {
            log.warn("Failed to call/parse TMAP pedestrian route: {}", e.toString());
            return null;
        }
    }

    /**
     * 자동차/보행자 공통 응답 파서
     * - FeatureCollection.features[*].properties.totalDistance/totalTime 사용
     */
    private TmapLegMetrics parseRouteMetrics(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode features = root.get("features");
            if (features != null && features.isArray()) {
                for (JsonNode feature : features) {
                    JsonNode props = feature.get("properties");
                    if (props == null) continue;

                    JsonNode distanceNode = props.get("totalDistance");
                    JsonNode timeNode = props.get("totalTime");
                    if (distanceNode != null && timeNode != null) {
                        double km = distanceNode.asDouble() / 1000.0;
                        double min = timeNode.asDouble() / 60.0;
                        return new TmapLegMetrics(km, min);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse TMAP route metrics: {}", e.toString());
        }
        return null;
    }

    /* ---------------- Estimate Fallback ---------------- */

    /**
     * 추정 로직(네트워크 실패 대비) — 기존 구현 유지
     * - 직선거리(line_km)는 하버사인으로 계산
     * - eta_walk_min: 보행 4.5km/h 가정
     * - eta_car_min : 차량 30km/h 가정
     * - 경로거리(route_*_km)는 추정이므로 line_km와 동일하게 둠
     */
    public TmapDtos.RouteResponse estimateFromOriginAndCandidates(TmapDtos.RouteRequest req) {

        double[] origin = parseLatLon(req.getOrigin());
        double oLat = origin[0];
        double oLon = origin[1];

        List<TmapDtos.Result> items = new ArrayList<>();
        if (req.getCandidates() != null) {
            for (TmapDtos.RouteRequest.Candidate cand : req.getCandidates()) {
                items.add(estimateForSingleCandidate(req, oLat, oLon, cand));
            }
        }

        return TmapDtos.RouteResponse.builder()
                .items(items)
                .durationMs(0L)     // 네트워크 호출 없음
                .source("ESTIMATE")
                .cached(0)
                .build();
    }

    /**
     * 단일 후보에 대한 ESTIMATE 결과 생성 (공용)
     */
    private TmapDtos.Result estimateForSingleCandidate(TmapDtos.RouteRequest req,
                                                       double oLat, double oLon,
                                                       TmapDtos.RouteRequest.Candidate cand) {

        double dLat = getDouble(cand.getLat());
        double dLon = getDouble(cand.getLon());

        // 1) 직선거리 계산(km) — 하버사인
        double lineKm = haversineKm(oLat, oLon, dLat, dLon);

        // 2) 옵션에 따라 모드별 ETA 계산(끄면 null)
        boolean wantCar = (req.getOptions() == null) || Boolean.TRUE.equals(req.getOptions().isCar());
        boolean wantWalk = (req.getOptions() == null) || Boolean.TRUE.equals(req.getOptions().isWalk());

        Double etaWalk = wantWalk ? estimateWalk(lineKm) : null; // 4.5km/h 가정
        Double etaCar = wantCar ? estimateCar(lineKm) : null;    // 30km/h 가정

        return TmapDtos.Result.builder()
                .hpid(cand.getHpid())
                .eta_car_min(etaCar)
                .eta_walk_min(etaWalk)
                .distance_line_km(lineKm)
                .route_car_km(lineKm)
                .route_walk_km(lineKm)
                .source("ESTIMATE") // 폴백 산출치임을 명시
                .build();
    }

    /* ---------------- Small utils ---------------- */

    // 보행 ETA 추정(분) : 4.5 km/h
    private double estimateWalk(double lineKm) {
        return (lineKm / 4.5) * 60.0;
    }

    // 차량 ETA 추정(분) : 30 km/h
    private double estimateCar(double lineKm) {
        return (lineKm / 30.0) * 60.0;
    }

    // "lat,lon" 문자열 → [lat, lon]
    private static double[] parseLatLon(String center) {
        String[] p = center.split(",");
        return new double[]{Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim())};
    }

    // BigDecimal → double (null 안전)
    private static double getDouble(BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }

    /** 하버사인 직선거리(km) — 네트워크 실패 시 추정 전용 */
    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    /**
     * 자동차/보행 공통 메트릭 홀더
     */
    private static class TmapLegMetrics {
        final double distanceKm;
        final double etaMin;

        TmapLegMetrics(double distanceKm, double etaMin) {
            this.distanceKm = distanceKm;
            this.etaMin = etaMin;
        }
    }
}
