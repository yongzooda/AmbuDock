package com.example.ambudock.service;

import com.example.ambudock.dto.tmap.TmapDtos;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TMAP OpenAPI 호출/정규화 서비스
 *
 * - 후보 병원 K(≤10)에 대해 TMAP OpenAPI 를 직접 호출해서
 *   차량/도보 ETA, 경로거리(route_*_km), 직선거리(distance_line_km)를 계산
 * - 실패(타임아웃/오류/400 등) 시, 직선거리 기반 추정치(source="ESTIMATE")로 대체
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

    /* ---------------- WebClient 재사용 ---------------- */

    /** 마지막 사용한 baseUrl */
    private volatile String lastBaseUrl = null;
    /** 재사용할 WebClient (baseUrl 변경 시 교체) */
    private volatile WebClient cachedClient = null;

    /**
     * baseUrl + appKey 로 WebClient를 제공.
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
                String key = tmapAppKey == null ? "" : tmapAppKey.trim();
                cachedClient = WebClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("appKey", key)
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
     * - TMAP OpenAPI 정상 응답이면 source="TMAP", 모두 실패하면 ESTIMATE 폴백
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
        // 1) 입력 형식 방어: origin은 "lat,lon" 형태여야 함
        if (req == null || req.getOrigin() == null || req.getOrigin().indexOf(',') < 0) {
            throw new IllegalArgumentException("origin format must be 'lat,lon'");
        }

        int timeoutMs = Math.max(500, tmapTimeoutMs);

        // "lat,lon" 파싱
        double[] origin = parseLatLon(req.getOrigin());
        double oLat = origin[0];
        double oLon = origin[1];

        boolean wantCar = req.getOptions() == null || Boolean.TRUE.equals(req.getOptions().isCar());
        boolean wantWalk = req.getOptions() == null || Boolean.TRUE.equals(req.getOptions().isWalk());

        long startNanos = System.nanoTime();
        List<TmapDtos.Result> items = new ArrayList<>();

        if (req.getCandidates() != null) {
            for (TmapDtos.RouteRequest.Candidate cand : req.getCandidates()) {
                double dLat = getDouble(cand.getLat());
                double dLon = getDouble(cand.getLon());

                Double etaCarMin = null;
                Double etaWalkMin = null;
                Double routeCarKm = null;
                Double routeWalkKm = null;
                Double lineKm = null;

                // --- 1) 자동차 경로 ---
                if (wantCar) {
                    try {
                        TmapRouteMetrics car = callCarRoute(oLat, oLon, dLat, dLon, timeoutMs);
                        if (car != null) {
                            etaCarMin = car.etaMin;
                            routeCarKm = car.distanceKm;
                        }
                    } catch (Exception e) {
                        log.warn("TMAP car route error: {}", e.toString());
                    }
                }

                // --- 2) 보행자 경로 ---
                if (wantWalk) {
                    try {
                        TmapRouteMetrics walk = callPedestrianRoute(oLat, oLon, dLat, dLon, timeoutMs);
                        if (walk != null) {
                            etaWalkMin = walk.etaMin;
                            routeWalkKm = walk.distanceKm;
                        }
                    } catch (Exception e) {
                        log.warn("TMAP pedestrian route error: {}", e.toString());
                    }
                }

                // --- 3) 직선 거리 API ---
                try {
                    lineKm = callDistanceApi(oLat, oLon, dLat, dLon, timeoutMs);
                } catch (Exception e) {
                    log.warn("TMAP /routes/distance error: {}", e.toString());
                }
                if (lineKm == null) {
                    // 마지막 방어: 하버사인
                    lineKm = haversineKm(oLat, oLon, dLat, dLon);
                }

                // --- 4) ETA/route 정보가 전부 null이면 ESTIMATE 폴백 ---
                if (etaCarMin == null && etaWalkMin == null && routeCarKm == null && routeWalkKm == null) {
                    if (wantCar) {
                        etaCarMin = estimateCar(lineKm);
                        routeCarKm = lineKm;
                    }
                    if (wantWalk) {
                        etaWalkMin = estimateWalk(lineKm);
                        routeWalkKm = lineKm;
                    }
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
            }
        }

        long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        return TmapDtos.RouteResponse.builder()
                .items(items)
                .durationMs(durationMs)
                .source("TMAP")
                .cached(0)
                .build();
    }

    /* ---------------- 실제 TMAP 호출 helpers ---------------- */

    /**
     * 자동차 경로안내 API:
     *  POST /tmap/routes?version=1
     *  body: { startX, startY, endX, endY, ... }
     *  응답: features[0].properties.totalDistance(m), totalTime(sec)
     */
    private TmapRouteMetrics callCarRoute(double startLat, double startLon,
                                          double endLat, double endLon,
                                          int timeoutMs) {

        Map<String, Object> body = new HashMap<>();
        body.put("startX", startLon); // X = 경도
        body.put("startY", startLat); // Y = 위도
        body.put("endX", endLon);
        body.put("endY", endLat);
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        body.put("searchOption", 0); // 기본 옵션

        JsonNode json = client().post()
                .uri(uriBuilder -> uriBuilder
                        .path("/tmap/routes")
                        .queryParam("version", 1)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        if (json == null) {
            return null;
        }

        JsonNode props = json.path("features").path(0).path("properties");
        if (props.isMissingNode()) {
            return null;
        }

        long totalDistanceM = props.path("totalDistance").asLong(0);
        long totalTimeSec = props.path("totalTime").asLong(0);
        if (totalDistanceM <= 0 || totalTimeSec <= 0) {
            return null;
        }

        double distanceKm = totalDistanceM / 1000.0;
        double etaMin = totalTimeSec / 60.0;
        return new TmapRouteMetrics(distanceKm, etaMin);
    }

    /**
     * 보행자 경로안내 API:
     *  POST /tmap/routes/pedestrian?version=1
     *  body: { startX, startY, endX, endY, startName, endName }
     *  응답: features[0].properties.totalDistance(m), totalTime(sec)
     */
    private TmapRouteMetrics callPedestrianRoute(double startLat, double startLon,
                                                 double endLat, double endLon,
                                                 int timeoutMs) {

        Map<String, Object> body = new HashMap<>();
        body.put("startX", startLon); // X = 경도
        body.put("startY", startLat); // Y = 위도
        body.put("endX", endLon);
        body.put("endY", endLat);
        body.put("startName", "출발지");
        body.put("endName", "도착지");

        JsonNode json = client().post()
                .uri(uriBuilder -> uriBuilder
                        .path("/tmap/routes/pedestrian")
                        .queryParam("version", 1)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        if (json == null) {
            return null;
        }

        JsonNode props = json.path("features").path(0).path("properties");
        if (props.isMissingNode()) {
            return null;
        }

        long totalDistanceM = props.path("totalDistance").asLong(0);
        long totalTimeSec = props.path("totalTime").asLong(0);
        if (totalDistanceM <= 0 || totalTimeSec <= 0) {
            return null;
        }

        double distanceKm = totalDistanceM / 1000.0;
        double etaMin = totalTimeSec / 60.0;
        return new TmapRouteMetrics(distanceKm, etaMin);
    }

    /**
     * 직선 거리 계산 API:
     *  GET /tmap/routes/distance?version=1&startX=..&startY=..&endX=..&endY=..
     *  응답: distanceInfo.distance (meter)
     */
    private Double callDistanceApi(double startLat, double startLon,
                                   double endLat, double endLon,
                                   int timeoutMs) {

        JsonNode json = client().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tmap/routes/distance")
                        .queryParam("version", 1)
                        .queryParam("startX", startLon)
                        .queryParam("startY", startLat)
                        .queryParam("endX", endLon)
                        .queryParam("endY", endLat)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        if (json == null) {
            return null;
        }

        long distanceM = json.path("distanceInfo").path("distance").asLong(0);
        if (distanceM <= 0) {
            return null;
        }
        return distanceM / 1000.0;
    }

    /* ---------------- 내부 공용 타입/유틸 ---------------- */

    /** 자동차/보행자 공통 metric */
    private static final class TmapRouteMetrics {
        final double distanceKm;
        final double etaMin;

        private TmapRouteMetrics(double distanceKm, double etaMin) {
            this.distanceKm = distanceKm;
            this.etaMin = etaMin;
        }
    }

    /**
     * 전체 폴백용 (네트워크 아예 안 될 때 등)를 위해 기존 추정 로직 유지
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

                double dLat = getDouble(cand.getLat());
                double dLon = getDouble(cand.getLon());

                double lineKm = haversineKm(oLat, oLon, dLat, dLon);

                Double etaWalk = Boolean.FALSE.equals(req.getOptions() == null ? null : req.getOptions().isWalk())
                        ? null : estimateWalk(lineKm);
                Double etaCar = Boolean.FALSE.equals(req.getOptions() == null ? null : req.getOptions().isCar())
                        ? null : estimateCar(lineKm);

                items.add(TmapDtos.Result.builder()
                        .hpid(cand.getHpid())
                        .eta_car_min(etaCar)
                        .eta_walk_min(etaWalk)
                        .distance_line_km(lineKm)
                        .route_car_km(lineKm)
                        .route_walk_km(lineKm)
                        .source("ESTIMATE")
                        .build());
            }
        }

        return TmapDtos.RouteResponse.builder()
                .items(items)
                .durationMs(0L)
                .source("ESTIMATE")
                .cached(0)
                .build();
    }

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
}
