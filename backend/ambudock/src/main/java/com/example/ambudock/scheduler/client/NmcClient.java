package com.example.ambudock.scheduler.client;

import com.example.ambudock.dto.NmcStatusDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NmcClient {

    @Value("${app.nmc.timeout_ms}")
    private int timeoutMs;

    @Value("${app.nmc.openapi.base}")
    private String openApiBase;

    @Value("${app.nmc.openapi.endpoint}")
    private String endpoint;

    @Value("${app.nmc.openapi.service_key}")
    private String serviceKey;

    // JSON이 아니라 XML을 파싱하기 위한 Mapper (Spring Boot가 자동으로 주입)
    private final XmlMapper xmlMapper;

    /**
     * 지역(시/도, 시/군/구)을 지정해서 수집한다. 페이징은 내부에서 흡수.
     */
    public List<NmcStatusDto> fetchLatestStatuses(String stage1, String stage2) {
        final int pageSize = 100;      // 응답을 좀 더 빨리 받기 위해 100 권장
        final int blockMs  = Math.max(3500, timeoutMs + 2000); // block용 여유 타임아웃 (3.5s 이상)
        int pageNo = 1;

        List<NmcStatusDto> acc = new ArrayList<>();
        try {
            while (true) {
                String xml = callNmcRaw(pageNo, pageSize, stage1, stage2, blockMs);
                if (xml == null) break;

                // XML → JsonNode (root 태그 = <response>)
                JsonNode root = xmlMapper.readTree(xml);

                // 응답 메타 파싱 (XML 구조에 맞게 response 바로 아래)
                JsonNode header = root.path("header");
                JsonNode body   = root.path("body");

                String resultCode = header.path("resultCode").asText(null);
                String resultMsg  = header.path("resultMsg").asText(null);
                int totalCount    = body.path("totalCount").asInt(-1);

                // 첫 페이지에서 전체 카운트/정상 여부 로그
                if (pageNo == 1) {
                    log.info("NMC header: stage1={}, stage2={}, resultCode={}, resultMsg={}, totalCount={}",
                            stage1, stage2, resultCode, resultMsg, totalCount);

                    if (totalCount == 0) {
                        log.warn("NMC raw response (totalCount=0): {}", xml);
                    }
                }

                // 비정상 코드면 즉시 중단
                if (!"00".equals(resultCode)) {
                    log.warn("NMC result NOT OK → stop paging: stage1={}, stage2={}, resultCode={}, resultMsg={}",
                            stage1, stage2, resultCode, resultMsg);
                    break;
                }

                JsonNode items = body
                        .path("items")
                        .path("item");   // <items><item>...</item></items>

                if (items.isMissingNode() || items.isNull()) break;

                if (items.isArray()) {
                    for (JsonNode it : items) {
                        acc.add(mapToDto(it));
                    }
                    if (items.size() < pageSize) break; // 마지막 페이지
                } else {
                    acc.add(mapToDto(items));            // 단일 객체 케이스
                    break;
                }
                pageNo++;
            }
            return acc;
        } catch (Exception ex) {
            log.warn("NmcClient fetchLatestStatuses failed: stage1={}, stage2={}, cause={}",
                    stage1, stage2, ex.toString());
            return Collections.emptyList();
        }
    }

    /**
     * NMC를 1페이지 호출해서 원본 XML을 문자열로 반환.
     * 공공데이터포털 샘플 코드 스타일(HttpURLConnection + URLEncoder) 그대로 사용.
     */
    private String callNmcRaw(int pageNo, int pageSize, String stage1, String stage2, int blockMs) {
        try {
            // base + endpoint → https://apis.data.go.kr/B552657/ErmctInfoInqireService/getEmrrmRltmUsefulSckbdInfoInqire
            String base = openApiBase.endsWith("/") ? openApiBase : openApiBase + "/";
            StringBuilder urlBuilder = new StringBuilder(base).append(endpoint);

            // ?serviceKey=...&STAGE1=...&STAGE2=...&pageNo=...&numOfRows=...
            urlBuilder.append("?")
                    .append(URLEncoder.encode("serviceKey", StandardCharsets.UTF_8))
                    .append("=")
                    .append(serviceKey);

            urlBuilder.append("&")
                    .append(URLEncoder.encode("STAGE1", StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(stage1, StandardCharsets.UTF_8));

            urlBuilder.append("&")
                    .append(URLEncoder.encode("STAGE2", StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(stage2, StandardCharsets.UTF_8));

            urlBuilder.append("&")
                    .append(URLEncoder.encode("pageNo", StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(String.valueOf(pageNo), StandardCharsets.UTF_8));

            urlBuilder.append("&")
                    .append(URLEncoder.encode("numOfRows", StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(String.valueOf(pageSize), StandardCharsets.UTF_8));

            String url = urlBuilder.toString();
            log.info("NMC RAW (HttpURLConnection) URL={}", url);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            // XML 응답을 기대함
            conn.setRequestProperty("Accept", "application/xml");
            conn.setConnectTimeout(blockMs);
            conn.setReadTimeout(blockMs);

            int code = conn.getResponseCode();
            log.info("NMC RAW HTTP status={}", code);

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                            StandardCharsets.UTF_8
                    )
            )) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                String xml = sb.toString();
                log.info("NMC RAW (first 300 chars)={}",
                        xml.length() > 300 ? xml.substring(0, 300) + "..." : xml);
                return xml;
            }
        } catch (Exception e) {
            log.warn("NMC RAW call failed: stage1={}, stage2={}, cause={}", stage1, stage2, e.toString());
            return null;
        }
    }

    // ---------- mapping (NmcStatusDto 필드에 맞춤) ----------
    private NmcStatusDto mapToDto(JsonNode n) {
        NmcStatusDto dto = new NmcStatusDto();
        dto.setHpid(text(n, "hpid"));
        dto.setDutyname(text(n, "dutyname", "dutyName"));
        dto.setDutytel3(text(n, "dutytel3", "dutyTel3"));
        dto.setHvidate(parseHvidateToInstant(text(n, "hvidate"))); // 문자열 → Instant(UTC)

        dto.setHvec(intOrNull(n, "hvec"));
        dto.setHv2(intOrNull(n, "hv2"));
        dto.setHv3(intOrNull(n, "hv3"));
        dto.setHv4(intOrNull(n, "hv4"));
        dto.setHv5(intOrNull(n, "hv5"));
        dto.setHv6(intOrNull(n, "hv6"));
        dto.setHvoc(intOrNull(n, "hvoc"));
        return dto;
    }

    private static String text(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull()) {
                return v.asText();
            }
        }
        return null;
    }

    private static Integer intOrNull(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        try {
            return Integer.parseInt(v.asText().trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    // ---------- hvidate parsing ----------
    /** 다양한 포맷 대비: ISO-8601 우선, 아니면 KST 가정 후 UTC로 변환 */
    private static Instant parseHvidateToInstant(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        String trimmed = s.trim();

        // 1) ISO-8601 (Z/오프셋 포함)
        try {
            return Instant.parse(trimmed);
        } catch (Exception ignore) {
        }

        // 2) 대표 로컬 포맷(KST 가정)
        final ZoneId KST = ZoneId.of("Asia/Seoul");
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd a h:mm:ss").withLocale(Locale.KOREAN),
                DateTimeFormatter.ofPattern("yyyy-MM-dd a h:mm").withLocale(Locale.KOREAN)
        );
        for (DateTimeFormatter f : fmts) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(trimmed, f);
                return ldt.atZone(KST).toInstant();
            } catch (Exception ignore) {
            }
        }

        // 3) Zoned 문자열
        try {
            return ZonedDateTime.parse(trimmed).toInstant();
        } catch (Exception ignore) {
        }

        return null;
    }

    /**
     * 디버그용: NMC 원본 XML을 1페이지만 그대로 반환.
     * - 브라우저에서 localhost 디버그 엔드포인트와 NMC 오픈API를 동시에 호출해서
     *   totalCount가 같은지 비교하기 위한 용도.
     */
    public String fetchRawOnce(String stage1, String stage2) {
        final int pageSize = 100;
        final int blockMs  = Math.max(3500, timeoutMs + 2000);

        String xml = callNmcRaw(1, pageSize, stage1, stage2, blockMs);
        log.info("NMC DEBUG raw response length={} chars", (xml != null ? xml.length() : 0));
        return xml;
    }
}
