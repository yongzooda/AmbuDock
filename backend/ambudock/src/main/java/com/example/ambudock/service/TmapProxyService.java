package com.example.ambudock.service;

import com.example.ambudock.dto.tmap.TmapDtos;
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
 * TMAP Proxy 호출/정규화 서비스
 *
 * - 프론트가 보낸 후보 병원 K(≤10)에 대해 백엔드 TMAP 프록시(/api/tmap/route)를 호출하고 표준 응답으로 변환
 * - 실패(타임아웃/오류/빈 응답) 시, 직선거리 기반 추정치(source="ESTIMATE")로 대체
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

    /* ---------------- WebClient 재사용 ---------------- */

    /** 마지막 사용한 baseUrl */
    private volatile String lastBaseUrl = null;
    /** 재사용할 WebClient (baseUrl 변경 시 교체) */
    private volatile WebClient cachedClient = null;

    /**
     * AppConfig의 TMAP_PROXY_URL을 읽어 WebClient를 제공.
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
                cachedClient = WebClient.builder().baseUrl(baseUrl).build();
                lastBaseUrl = baseUrl;
                log.info("TMAP proxy WebClient reinitialized. baseUrl={}", baseUrl);
            }
            return cachedClient;
        }
    }

    /* ---------------- Public API ---------------- */

    /**
     * 후보 K 병원에 대한 차량/도보 ETA와 거리 요청
     * - 캐시 키: origin + candidates + 옵션(car/walk)을 문자열로 묶어 MD5 (동일 입력 120s 재사용)
     * - 정상 응답(source="TMAP")이면 그대로 반환, 아니면 추정치(source="ESTIMATE")
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
        //1) 입력 형식 방어: origin은 "lat,lon" 형태여야 함
        if (req == null || req.getOrigin() == null || req.getOrigin().indexOf(',') < 0) {
            throw new IllegalArgumentException("origin format must be 'lat,lon'");
        }

        //2) 타임아웃(ms) 설정: 하한 500ms 보장
        int timeoutMs = Math.max(500, tmapTimeoutMs);

        try {
            // 3) 우리 백엔드의 TMAP 프록시를 호출 (실제 SK 호출은 프록시가 수행)


            TmapDtos.RouteResponse res = client().post() //WebClient 인스턴스(이미 구성된 baseUrl 사용)로 POST 요청 준비
                    .uri("/api/tmap/route") // 요청 URI: baseUrl + "/api/tmap/route"
                    .contentType(MediaType.APPLICATION_JSON) //Accept 헤더: 서버가 JSON 응답을 보내길 기대
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(req)         //요청 바디에 Java 객체(req)를 넣으면 Jackson이 JSON으로 직렬화해서 전송
                    .retrieve()         //요청을 전송하고(비동기) 응답을 받을 준비를 함
                    .bodyToMono(TmapDtos.RouteResponse.class)//응답 본문을 역직렬화
                    .timeout(Duration.ofMillis(timeoutMs))        // 네트워크 상한
                    .onErrorResume(e -> {           // 네트워크/서버 오류 시 비우고 폴백 유도
                        log.warn("TMAP proxy error ({} ms timeout). cause={}", timeoutMs, e.toString());
                        return Mono.empty();
                    })
                    .block(); //Mono를 블로킹해서 실제 결과 객체를 동기적으로 얻음

            // 5) 정상 응답: 아이템이 있으면 그대로 반환
            if (res != null && res.getItems() != null && !res.getItems().isEmpty()) {
                return res; // 정상 응답 (보통 source="TMAP")
            } else {
                // 6) 빈 응답: 추정치로 폴백
                log.warn("TMAP proxy responded empty. Fallback to ESTIMATE. origin={}, candidates={}",
                        req.getOrigin(), req.getCandidates() == null ? 0 : req.getCandidates().size());
            }
        } catch (Exception e) {
            // 7) 예외: 추정치로 폴백
            log.warn("TMAP proxy exception (fallback to ESTIMATE): {}", e.toString());
        }

        // 8) 폴백: 직선거리 기반 간이 추정(source="ESTIMATE") 생성 후 반환
        return estimateFromOriginAndCandidates(req);
    }

    /* ---------------- Estimate Fallback ---------------- */

    /**
     * 추정 로직(네트워크 실패 대비)
     * - 직선거리(line_km)는 하버사인으로 계산
     * - eta_walk_min: 보행 4.5km/h 가정
     * - eta_car_min : 차량 30km/h 가정
     * - 경로거리(route_*_km)는 추정이므로 line_km와 동일하게 둠
     * - 정상 상황에선 TMAP Proxy 결과(line_km 포함)를 우선 사용
     */
    public TmapDtos.RouteResponse estimateFromOriginAndCandidates(TmapDtos.RouteRequest req) {

        // "lat,lon" 형태 origin 파싱 → oLat/oLon
        double[] origin = parseLatLon(req.getOrigin());
        double oLat = origin[0];
        double oLon = origin[1];

        List<TmapDtos.Result> items = new ArrayList<>();
        if (req.getCandidates() != null) {
            for (TmapDtos.RouteRequest.Candidate cand : req.getCandidates()) {

                // 후보 좌표
                double dLat = getDouble(cand.getLat());
                double dLon = getDouble(cand.getLon());

                // 1) 직선거리 계산(km) — 하버사인
                double lineKm = haversineKm(oLat, oLon, dLat, dLon);

                // 2) 옵션에 따라 모드별 ETA 계산(끄면 null)
                Double etaWalk = Boolean.FALSE.equals(req.getOptions() == null ? null : req.getOptions().isWalk())
                        ? null : estimateWalk(lineKm); // 4.5km/h 가정
                Double etaCar = Boolean.FALSE.equals(req.getOptions() == null ? null : req.getOptions().isCar())
                        ? null : estimateCar(lineKm); // 30km/h 가정

                // 3) 결과 구성: route_*_km는 추정이므로 line_km로 동일 적용
                items.add(TmapDtos.Result.builder()
                        .hpid(cand.getHpid())
                        .eta_car_min(etaCar)
                        .eta_walk_min(etaWalk)
                        .distance_line_km(lineKm)
                        .route_car_km(lineKm)
                        .route_walk_km(lineKm)
                        .source("ESTIMATE") // 폴백 산출치임을 명시
                        .build());
            }
        }

        // 네트워크 호출이 없으므로 durationMs=0, 캐시 적중 개수는 미집계(cached=0)
        return TmapDtos.RouteResponse.builder()
                .items(items)
                .durationMs(0L)     // 네트워크 호출 없음
                .source("ESTIMATE")
                .cached(0)
                .build();
    }

    /* ---------------- Small utils ---------------- */

    // 보행 ETA 추정(분) : 4.5 km/h
    private double estimateWalk(double lineKm) { return (lineKm / 4.5) * 60.0; }

    // 차량 ETA 추정(분) : 30 km/h
    private double estimateCar(double lineKm)  { return (lineKm / 30.0) * 60.0; }

    // "lat,lon" 문자열 → [lat, lon]
    private static double[] parseLatLon(String center) {
        String[] p = center.split(",");
        return new double[] { Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()) };
    }

    // BigDecimal → double (null 안전)
    private static double getDouble(BigDecimal v) { return v == null ? 0d : v.doubleValue(); }

    /** 하버사인 직선거리(km) — 네트워크 실패 시 추정 전용 */
    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }
}
