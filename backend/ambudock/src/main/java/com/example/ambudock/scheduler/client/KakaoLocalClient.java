package com.example.ambudock.scheduler.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    @Value("${app.kakao.rest_api_key}")
    private String kakaoRestApiKey;

    @Value("${app.kakao.timeout_ms}")
    private int kakaoTimeoutMs;

    /** 기본 검색 결과 개수 (yml에 항목이 없으므로 코드 기본값 3) */
    private static final int DEFAULT_SEARCH_SIZE = 3;

    /** NMC dutyName → Kakao 검색용 alias 매핑 (일부 병원만 예외 처리) */
    private static final Map<String, String> HOSPITAL_QUERY_ALIAS = Map.ofEntries(
            Map.entry("의료법인서울효천의료재단에이치플러스양지병원", "에이치플러스양지병원"),
            Map.entry("고려대학교의과대학부속구로병원", "구로병원"),
            Map.entry("의료법인한전의료재단한일병원", "한일병원"),
            Map.entry("학교법인가톨릭학원가톨릭대학교서울성모병원", "서울성모병원"),
            Map.entry("학교법인고려중앙학원고려대학교의과대학부속병원(안암병원)", "안암병원")
    );

    /** 병원 dutyName을 Kakao 검색용 키워드로 보정 */
    private String normalizeHospitalKeyword(String query) {
        String alias = HOSPITAL_QUERY_ALIAS.getOrDefault(query, query);
        if (!alias.equals(query)) {
            log.info("Kakao keyword alias applied: '{}' -> '{}'", query, alias);
        }
        return alias;
    }


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
            log.warn("Kakao search skipped: REST API key is empty. Set app.kakao.rest_api_key (e.g. APP_KAKAO_REST_API_KEY env).");
            return Collections.emptyList();
        }
        int timeout = Math.max(500, kakaoTimeoutMs);

        // ★ 여기서 병원 dutyName이면 alias로 교체
        String effectiveQuery = normalizeHospitalKeyword(query);

        try {
            var mono = client().get()
                    .uri(uri -> uri.path("/v2/local/search/keyword.json")
                            .queryParam("query", effectiveQuery)
                            .queryParam("size", Math.max(1, size))
                            .build())
                    .retrieve()
                    .bodyToMono(KeywordResp.class);
            KeywordResp resp = mono.block(Duration.ofMillis(timeout));
            if (resp == null || resp.getDocuments() == null) return Collections.emptyList();
            return resp.getDocuments();
        } catch (WebClientResponseException e) {
            // 403 같은 상태코드/바디 확인용
            log.warn("Kakao keyword failed(q={} / effective={}): status={} body={}",
                    query, effectiveQuery, e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Kakao keyword failed(q={} / effective={}): {}",
                    query, effectiveQuery, e.toString());
            return Collections.emptyList();
        }
    }



    /** 좌표 → 행정구역 (가장 상위 1건 반환) */
    /** 좌표 → 행정구역 (법정동(B)만 반환; 없으면 null) */
    public Region reverseRegion(double lat, double lon) {
        if (isKeyMissing()) {
            log.warn("Kakao reverse-region skipped: REST API key is empty. Set app.kakao.rest_api_key (e.g. APP_KAKAO_REST_API_KEY env).");
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
        } catch (WebClientResponseException e) {
            log.warn("Kakao reverse failed(lat={},lon={}): status={} body={}",
                    lat, lon, e.getStatusCode(), e.getResponseBodyAsString());
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

        @JsonProperty("region_type")           // B(법정동) / H(행정동)
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
