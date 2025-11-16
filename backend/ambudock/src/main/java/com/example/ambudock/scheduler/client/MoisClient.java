package com.example.ambudock.scheduler.client;

import com.example.ambudock.dto.MoisAlertDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MoisClient
 *
 * 책임 분리 원칙에 따라 이 클래스는 "원문 수집/정규화"만 담당한다.
 *  - 심각도 판정(Severity), TTL 계산, 지역 매핑은 DisasterIngestService가 수행한다.
 *  - 여기서는 원문에서 얻을 수 있는 필수 필드(md101Sn, issuedAt, locationName, msg 등)와
 *    판정에 도움이 되는 '힌트 필드'(emrgStepName, hazardName)까지만 세팅한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MoisClient {

    @Value("${app.mois.base_url}")
    private String baseUrl;

    @Value("${app.mois.endpoint_path}")
    private String endpointPath;

    @Value("${app.mois.service_key}")
    private String serviceKey;

    @Value("${app.mois.timeout_ms:1500}")
    private int timeoutMs;

    @Value("${app.mois.default_rows:50}")
    private int defaultRows;

    /** 쿼리 기준 '오늘'을 계산할 타임존 (기본 KST). */
    @Value("${app.mois.query-zone:Asia/Seoul}")
    private String queryZone;

    /** 테스트 용 Clock(필요하면 @Autowired/생성자 주입으로 바꿀 수 있음). */
    private final Clock clock = Clock.systemUTC();

    /** Jackson ObjectMapper (스레드-세이프하게 재사용). */
    private final ObjectMapper om = new ObjectMapper();

    /* ======================================================================
     * 퍼블릭 API
     * ====================================================================== */

    /**
     * 오늘(쿼리존 기준) 이후 생성분의 최신 N건을 조회한다.
     * - DisasterIngestService가 주기적으로 호출하여 ingest 대상 원문을 가져간다.
     */
    public List<MoisAlertDto> fetchLatestAlerts() {
        ZoneId zid = ZoneId.of(queryZone);
        LocalDate today = LocalDate.now(zid);
        String sinceYmd = today.format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        return fetchAlerts(sinceYmd, null, 1, defaultRows);
    }

    /**
     * 실제 MOIS API 호출 및 원문 정규화.
     *
     * @param sinceYmd   YYYYMMDD (crtDt 파라미터)
     * @param regionName rgnNm (선택)
     * @param page       pageNo (1기반)
     * @param rows       numOfRows
     * @return MoisAlertDto 리스트 (issuedAt 내림차순 정렬)
     */
    public List<MoisAlertDto> fetchAlerts(String sinceYmd, String regionName, int page, int rows) {
        if (StringUtils.isBlank(serviceKey)) {
            log.warn("MOIS serviceKey is empty. Skip calling.");
            return List.of();
        }

        try {
            WebClient wc = newClient();

            // baseUrl + endpointPath를 합치고, serviceKey는 나중에 수동으로 붙인다.
            String normalizedBase = baseUrl.endsWith("/")
                    ? baseUrl.substring(0, baseUrl.length() - 1)
                    : baseUrl;
            String normalizedPath = endpointPath.startsWith("/")
                    ? endpointPath
                    : "/" + endpointPath;

            // 1) serviceKey를 제외한 나머지 파라미터를 UriComponentsBuilder로 구성
            String urlWithoutKey = org.springframework.web.util.UriComponentsBuilder
                    .fromHttpUrl(normalizedBase + normalizedPath)
                    .queryParam("returnType", "json")
                    .queryParam("pageNo", Math.max(1, page))
                    .queryParam("numOfRows", Math.max(1, rows))
                    .queryParamIfPresent("crtDt", Optional.ofNullable(sinceYmd)
                            .filter(org.apache.commons.lang3.StringUtils::isNotBlank))
                    .queryParamIfPresent("rgnNm", Optional.ofNullable(regionName)
                            .filter(org.apache.commons.lang3.StringUtils::isNotBlank))
                    // ★ 여기서는 .encode(...) 호출 안 함
                    .build()
                    .toUriString();

            // 2) serviceKey는 포털에서 받은 그대로(이미 인코딩 되어 있을 수 있으므로) 수동으로 붙임
            String sep = urlWithoutKey.contains("?") ? "&" : "?";
            String url = urlWithoutKey + sep + "serviceKey=" + serviceKey;

            // 실제 호출 URL 로그
            log.debug("MOIS request URL={}", url);


            JsonNode root = wc.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, res -> res.createException().flatMap(Mono::error))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(Math.max(500, timeoutMs)))
                    .map(this::readTreeSafe)
                    .onErrorResume(ex -> {
                        log.warn("MOIS fetch failed: {} cause={}", url, ex.toString());
                        return Mono.just(om.createObjectNode());
                    })
                    .block();

            if (root == null || root.isEmpty()) return List.of();

            ArrayNode items = extractItemsArray(root);
            if (items == null || items.isEmpty()) return List.of();

            List<MoisAlertDto> list = new ArrayList<>(items.size());
            for (JsonNode n : items) {
                MoisAlertDto dto = mapToDtoWithSpec(n);
                if (dto != null) list.add(dto);
            }

            // issuedAt 내림차순 (null 뒤로)
            return list.stream()
                    .sorted(Comparator.comparing(
                            (MoisAlertDto d) -> Optional.ofNullable(d.getIssuedAt()).orElse(Instant.EPOCH)
                    ).reversed())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("MOIS fetch error: {}", e.toString());
            return List.of();
        }
    }


    /* ======================================================================
     * 내부 유틸
     * ====================================================================== */

    /**
     * timeout/핸들러가 설정된 WebClient 생성.
     * - 호출 빈도가 높다면 @Bean 단일 인스턴스로 재사용해도 됨.
     */
    private WebClient newClient() {
        int connectTimeout = Math.max(500, timeoutMs);

        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(connectTimeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(connectTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(connectTimeout, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 안전한 JSON 파싱. 실패 시 빈 ObjectNode 반환. */
    private JsonNode readTreeSafe(String raw) {
        try {
            return om.readTree(raw);
        } catch (Exception e) {
            log.warn("MOIS JSON parse error: bodyLen={} cause={}", raw == null ? 0 : raw.length(), e.toString());
            return om.createObjectNode();
        }
    }

    /**
     * 다양한 스펙을 고려하여 항목 배열을 찾아낸다.
     *  - 우선순위: data 배열 → response.body.items → DFS로 서명 검사
     *  - 항목의 대표 키: SN, CRT_DT, MSG_CN, RCPTN_RGN_NM 등
     */
    private ArrayNode extractItemsArray(JsonNode root) {
        if (root.has("data") && root.get("data").isArray()) {
            return (ArrayNode) root.get("data");
        }
        if (root.path("response").path("body").has("items")
                && root.path("response").path("body").get("items").isArray()) {
            return (ArrayNode) root.path("response").path("body").get("items");
        }

        Deque<JsonNode> st = new ArrayDeque<>();
        st.push(root);
        while (!st.isEmpty()) {
            JsonNode cur = st.pop();

            if (cur.isArray() && cur.size() > 0 && cur.get(0).isObject()) {
                JsonNode first = cur.get(0);
                if (first.has("MSG_CN") || first.has("RCPTN_RGN_NM") || first.has("CRT_DT") || first.has("SN")) {
                    return (ArrayNode) cur;
                }
            }
            if (cur.isObject()) cur.fields().forEachRemaining(e -> st.push(e.getValue()));
            else if (cur.isArray()) cur.forEach(st::push);
        }
        return null;
    }

    /**
     * 스펙(이미지) 기반으로 JsonNode → MoisAlertDto 매핑.
     *  - 여기서는 '힌트 필드'까지만 세팅(emrgStepName, hazardName).
     *  - severity/TTL 계산은 절대 하지 않는다.
     */
    private MoisAlertDto mapToDtoWithSpec(JsonNode n) {
        // ID: md101Sn(신) 또는 SN(구)
        String md101Sn = firstNonBlank(text(n, "md101Sn"), text(n, "SN"));

        // 메시지 본문
        String msg = firstNonBlank(text(n, "msg"), text(n, "MSG_CN"));

        // 지역명(수신지역명)
        String locationName = firstNonBlank(text(n, "locationName"), text(n, "RCPTN_RGN_NM"));

        // locationId는 신스펙에서 제공되지 않을 수 있음 → null 허용
        String locationId = text(n, "location_id");

        // 발행시각: CRT_DT → REG_YMD 순으로 시도(KST 해석 → Instant(UTC))
        Instant issuedAt = parseToInstantPreferKst(n, "CRT_DT", "REG_YMD");

        // 원문 힌트: 단계명/재난유형명
        String emrgStepName = firstNonBlank(text(n, "emrgStepName"), text(n, "EMRG_STEP_NM"));
        String hazardName   = firstNonBlank(text(n, "hazardName"),   text(n, "DST_SE_NM"));

        if (md101Sn == null && msg == null && locationName == null) {
            // 완전 무의미한 레코드는 버림(선택)
            return null;
        }

        return new MoisAlertDto(md101Sn, issuedAt, locationId, locationName, msg, emrgStepName, hazardName);
    }

    /**
     * 주어진 키들을 시도하여 날짜/시간 문자열을 KST로 해석하고 UTC Instant로 변환.
     * - keyPrimary 실패 시 keyFallback 사용.
     */
    private Instant parseToInstantPreferKst(JsonNode n, String keyPrimary, String keyFallback) {
        Instant p = parseToInstantKst(text(n, keyPrimary));
        if (p != null) return p;
        return parseToInstantKst(text(n, keyFallback));
    }

    /**
     * 여러 포맷을 허용하여 KST(Local) → UTC Instant 변환.
     * - 날짜만 있으면 KST 자정으로 간주.
     * - 오프셋 포함 ISO는 오프셋 존중.
     */
    private Instant parseToInstantKst(String s) {
        if (StringUtils.isBlank(s)) return null;
        final ZoneId KST = ZoneId.of("Asia/Seoul");

        String[] patterns = new String[]{
                "yyyy-MM-dd HH:mm:ss",
                "yyyy/MM/dd HH:mm:ss",
                "yyyyMMddHHmmss",
                "yyyy-MM-dd'T'HH:mm:ssXXX", // 2025-11-07T23:30:00+09:00
                "yyyy-MM-dd'T'HH:mm:ss",    // 오프셋 없으면 KST 가정
                "yyyyMMdd",
                "yyyy-MM-dd"
        };

        for (String p : patterns) {
            try {
                if ("yyyyMMdd".equals(p) || "yyyy-MM-dd".equals(p)) {
                    LocalDate d = LocalDate.parse(s, DateTimeFormatter.ofPattern(p));
                    return d.atStartOfDay(KST).toInstant();
                } else if ("yyyy-MM-dd'T'HH:mm:ssXXX".equals(p)) {
                    return OffsetDateTime.parse(s, DateTimeFormatter.ofPattern(p)).toInstant();
                } else {
                    LocalDateTime dt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p));
                    return dt.atZone(KST).toInstant();
                }
            } catch (DateTimeParseException ignore) {
                // 다음 패턴 시도
            } catch (Exception ignore) {
            }
        }

        // 마지막 시도: 자유 형식 ISO
        try {
            return Instant.parse(s);
        } catch (Exception ignore) {}

        log.debug("MOIS date parse failed for value='{}'", s);
        return null;
    }

    /* ---------------- 작은 헬퍼 ---------------- */

    private String text(JsonNode n, String key) {
        if (n == null || key == null) return null;
        JsonNode v = n.get(key);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : ((b != null && !b.isBlank()) ? b : null);
    }
}
