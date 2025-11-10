package com.example.ambudock.scheduler.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Kakao Local REST Client
 *
 * - /v2/local/search/keyword.json  : 키워드(병원명 등) 검색 → 좌표/주소
 * - /v2/local/geo/coord2regioncode : 좌표 → 행정구역 코드/명
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoLocalClient {

    @Value("${app.kakao.rest_api_key:}")
    private String kakaoRestApiKey;

    @Value("${app.kakao.timeout_ms:1500}")
    private int kakaoTimeoutMs;

    /** 기본 검색 결과 개수 (yml에 항목이 없으므로 코드 기본값 3) */
    private static final int DEFAULT_SEARCH_SIZE = 3;

    private WebClient client() {
        return WebClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + kakaoRestApiKey)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 키워드 검색 (size 기본값 사용) */
    public List<Place> searchByKeyword(String query) {
        return searchByKeyword(query, DEFAULT_SEARCH_SIZE);
    }

    /** 키워드 검색 (size 지정) */
    public List<Place> searchByKeyword(String query, int size) {
        if (isKeyMissing()) {
            log.warn("Kakao search skipped: REST API key is empty. Set app.kakao.rest_api_key or KAKAO_REST_API_KEY env.");
            return Collections.emptyList();
        }
        int timeout = Math.max(500, kakaoTimeoutMs);
        try {
            var mono = client().get()
                    .uri(uri -> uri.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("size", Math.max(1, size))
                            .build())
                    .retrieve()
                    .bodyToMono(KeywordResp.class);
            KeywordResp resp = mono.block(Duration.ofMillis(timeout));
            if (resp == null || resp.getDocuments() == null) return Collections.emptyList();
            return resp.getDocuments();
        } catch (Exception e) {
            log.warn("Kakao keyword failed(q={}): {}", query, e.toString());
            return Collections.emptyList();
        }
    }

    /** 좌표 → 행정구역 (가장 상위 1건 반환) */
    /** 좌표 → 행정구역 (법정동(B)만 반환; 없으면 null) */
    public Region reverseRegion(double lat, double lon) {
        if (isKeyMissing()) {
            log.warn("Kakao reverse-region skipped: REST API key is empty. Set app.kakao.rest_api_key or KAKAO_REST_API_KEY env.");
            return null;
        }
        int timeout = Math.max(500, kakaoTimeoutMs);
        try {
            var mono = client().get()
                    .uri(uri -> uri.path("/v2/local/geo/coord2regioncode.json")
                            .queryParam("x", lon) // 경도
                            .queryParam("y", lat) // 위도
                            .build())
                    .retrieve()
                    .bodyToMono(RegionCodeResp.class);

            RegionCodeResp resp = mono.block(Duration.ofMillis(timeout));
            if (resp == null || resp.getDocuments() == null || resp.getDocuments().isEmpty()) return null;

            // ★ 법정동(B) 우선 선택, 없으면 null
            for (Region d : resp.getDocuments()) {
                if ("B".equals(d.getRegionType())) return d;
            }
            log.warn("Kakao reverse-region: no LEGAL(B) region found for ({},{})", lat, lon);
            return null;
        } catch (Exception e) {
            log.warn("Kakao reverse failed(lat={},lon={}): {}", lat, lon, e.toString());
            return null;
        }
    }

    private boolean isKeyMissing() {
        return kakaoRestApiKey == null || kakaoRestApiKey.isBlank();
    }

    // ================= DTO =================

    @Data
    public static class KeywordResp {
        private List<Place> documents;
    }

    /**
     * Place DTO — 카멜케이스 필드로 제공(카카오 스네이크 응답을 @JsonProperty로 매핑)
     * - x, y는 문자열(String) → 서비스에서 Double.parseDouble 등으로 변환
     */
    @Data
    public static class Place {
        @JsonProperty("place_name")
        private String placeName;

        @JsonProperty("road_address_name")
        private String roadAddressName;

        @JsonProperty("address_name")
        private String addressName;

        /** 경도(String) */
        @JsonProperty("x")
        private String x;

        /** 위도(String) */
        @JsonProperty("y")
        private String y;

        private String phone;
    }

    @Data
    public static class RegionCodeResp {
        private List<Region> documents;
    }

    /**
     * Region DTO — 카멜케이스 필드 제공
     */
    @Data
    public static class Region {
        private String code; // 행정구역 코드

        @JsonProperty("region_type")           // ← 추가: B(법정동) / H(행정동)
        private String regionType;

        @JsonProperty("region_1depth_name")
        private String region1depthName;  // 시/도
        @JsonProperty("region_2depth_name")
        private String region2depthName;  // 시/군/구
        @JsonProperty("region_3depth_name")
        private String region3depthName;  // 읍/면/동
        @JsonProperty("address_name")
        private String addressName;       // 전체 주소
    }
}
