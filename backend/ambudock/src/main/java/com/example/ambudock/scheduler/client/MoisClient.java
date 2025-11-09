package com.example.ambudock.scheduler.client;

import com.example.ambudock.dto.MoisAlertDto;
import com.example.ambudock.domain.common.enums.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.HttpStatusCode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MoisClient {

    @Value("${app.mois.base_url:https://www.safetydata.go.kr}")
    private String baseUrl;

    @Value("${app.mois.endpoint_path:/v2/api/DSSF-IP-00247}")
    private String endpointPath;

    @Value("${app.mois.service_key:}")
    private String serviceKey;

    @Value("${app.mois.timeout_ms:2000}")
    private int timeoutMs;

    @Value("${app.mois.default_rows:100}")
    private int defaultRows;

    private final ObjectMapper om = new ObjectMapper();

    private WebClient newClient() {
        int connectTimeout = Math.max(500, timeoutMs);
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(connectTimeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(connectTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler(connectTimeout, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(http)) // ✅ 수정
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 오늘(KST) 생성 기준 최신 N건 */
    public List<MoisAlertDto> fetchLatestAlerts() {
        LocalDate todayKST = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return fetchAlerts(todayKST.format(DateTimeFormatter.BASIC_ISO_DATE), null, 1, defaultRows);
    }

    /** since(YYYYMMDD) 이후, rgnNm(선택) 필터 */
    public List<MoisAlertDto> fetchAlerts(String sinceYmd, String regionName, int page, int rows) {
        if (StringUtils.isBlank(serviceKey)) {
            log.warn("MOIS serviceKey is empty. Skip call.");
            return Collections.emptyList();
        }

        try {
            WebClient wc = newClient();

            // NOTE: 호출 403 뜨면 인코딩을 제거하고 원본 키를 그대로 전달해 보세요.
            String encodedKey = URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);

            WebClient.RequestHeadersSpec<?> spec = wc.get()
                    .uri(builder -> {
                        var ub = builder.path(endpointPath)
                                .queryParam("serviceKey", encodedKey)
                                .queryParam("returnType", "json")
                                .queryParam("pageNo", Math.max(page, 1))
                                .queryParam("numOfRows", Math.max(rows, 1));
                        if (StringUtils.isNotBlank(sinceYmd)) {
                            ub.queryParam("crtDt", sinceYmd);
                        }
                        if (StringUtils.isNotBlank(regionName)) {
                            ub.queryParam("rgnNm", regionName);
                        }
                        return ub.build();
                    })
                    .accept(MediaType.APPLICATION_JSON);

            JsonNode root = spec.retrieve()
                    .onStatus(HttpStatusCode::isError,            // ✅ HttpStatusCode 로 변경
                            res -> res.createException().flatMap(Mono::error)) // ✅ 반환타입도 정확
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(Math.max(500, timeoutMs)))
                    .map(this::readTreeSafe)
                    .onErrorResume(ex -> {
                        log.warn("MOIS fetch failed: url={}{}, cause={}", baseUrl, endpointPath, ex.toString());
                        return Mono.just(om.createObjectNode());
                    })
                    .block();

            if (root == null || root.isEmpty()) return Collections.emptyList();

            ArrayNode items = extractItemsArray(root);
            if (items == null || items.isEmpty()) return Collections.emptyList();

            List<MoisAlertDto> list = new ArrayList<>(items.size());
            for (JsonNode n : items) {
                list.add(mapToDto(n));
            }

            // 최신 issuedAt 기준 내림차순
            return list.stream()
                    .sorted(Comparator.comparing(
                            (MoisAlertDto d) -> Optional.ofNullable(d.getIssuedAt()).orElse(Instant.EPOCH)
                    ).reversed())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("MOIS fetch error: cause={}", e.toString());
            return Collections.emptyList();
        }
    }

    /* -------------------- 내부 유틸 -------------------- */

    private JsonNode readTreeSafe(String raw) {
        try { return om.readTree(raw); }
        catch (Exception e) {
            log.warn("MOIS JSON parse error: body(length={}), cause={}", raw == null ? 0 : raw.length(), e.toString());
            return om.createObjectNode();
        }
    }

    private ArrayNode extractItemsArray(JsonNode root) {
        if (root.has("data") && root.get("data").isArray()) {
            return (ArrayNode) root.get("data");
        }
        if (root.path("response").path("body").has("items")
                && root.path("response").path("body").get("items").isArray()) {
            return (ArrayNode) root.path("response").path("body").get("items");
        }
        Deque<JsonNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            JsonNode cur = stack.pop();
            if (cur.isArray() && cur.size() > 0 && cur.get(0).isObject()) {
                JsonNode first = cur.get(0);
                if (first.has("MSG_CN") || first.has("RCPTN_RGN_NM") || first.has("CRT_DT")) {
                    return (ArrayNode) cur;
                }
            }
            if (cur.isObject()) cur.fields().forEachRemaining(e -> stack.push(e.getValue()));
            else if (cur.isArray()) cur.forEach(stack::push);
        }
        return null;
    }

    /** 신/구 스펙 → 기존 MoisAlertDto 로 매핑 */
    private MoisAlertDto mapToDto(JsonNode n) {
        String md101Sn = firstNonBlank(text(n, "md101Sn"), text(n, "SN"));
        String msg = firstNonBlank(text(n, "msg"), text(n, "MSG_CN"));
        String locationName = firstNonBlank(text(n, "locationName"), text(n, "RCPTN_RGN_NM"));
        String locationId = text(n, "location_id"); // 없을 수 있음

        String crtDt = text(n, "CRT_DT");
        String regYmd = text(n, "REG_YMD");
        Instant issuedAt = parseToInstantKST(crtDt);
        if (issuedAt == null) issuedAt = parseToInstantKST(regYmd);

        // 가능하면 심각도 유추(없으면 null → A4에서 보정/TTL 부여)
        String stepName = firstNonBlank(text(n, "severity"), text(n, "EMRG_STEP_NM"));
        Severity severity = deriveSeverity(stepName, msg);

        return new MoisAlertDto(md101Sn, issuedAt, locationId, locationName, msg, severity, null);
    }

    private String text(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : ((b != null && !b.isBlank()) ? b : null);
    }

    /** 다양한 포맷을 수용하고 KST로 해석 후 UTC Instant로 변환 */
    private Instant parseToInstantKST(String s) {
        if (StringUtils.isBlank(s)) return null;
        final ZoneId KST = ZoneId.of("Asia/Seoul");
        String[] patterns = new String[] {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy/MM/dd HH:mm:ss",
                "yyyyMMddHHmmss",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyyMMdd",
                "yyyy-MM-dd"
        };
        for (String p : patterns) {
            try {
                if (p.equals("yyyyMMdd") || p.equals("yyyy-MM-dd")) {
                    LocalDate d = LocalDate.parse(s, DateTimeFormatter.ofPattern(p));
                    return d.atStartOfDay(KST).toInstant();
                } else {
                    LocalDateTime dt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p));
                    return dt.atZone(KST).toInstant();
                }
            } catch (Exception ignore) {}
        }
        log.debug("MOIS date parse failed: '{}'", s);
        return null;
    }

    /** EMRG_STEP_NM/본문 키워드 기반 간단 심각도 유추 (없으면 null) */
    private Severity deriveSeverity(String stepName, String msg) {
        if (stepName == null && msg == null) return null;
        String s = stepName == null ? "" : stepName.toLowerCase();
        String m = msg == null ? "" : msg.toLowerCase();

        if (s.contains("경보") || s.contains("대피") || s.contains("통제")) return Severity.BLOCK;
        if (s.contains("주의") || s.contains("주의보")) return Severity.WARN;

        if (m.contains("대피") || m.contains("전면 통제") || m.contains("통제")) return Severity.BLOCK;
        if (m.contains("주의") || m.contains("강풍") || m.contains("호우") || m.contains("주의보")) return Severity.WARN;

        return null;
    }
}
