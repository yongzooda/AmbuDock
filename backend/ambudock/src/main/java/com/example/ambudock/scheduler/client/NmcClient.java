package com.example.ambudock.scheduler.client;

import com.example.ambudock.dto.NmcStatusDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NmcClient {

    @Value("${app.nmc.timeout_ms:1500}")
    private int timeoutMs;

    @Value("${app.nmc.openapi.base}")
    private String openApiBase;

    @Value("${app.nmc.openapi.endpoint}")
    private String endpoint;

    @Value("${app.nmc.openapi.service_key}")
    private String serviceKey;

    private final ObjectMapper om = new ObjectMapper();

    private WebClient wc() {
        return WebClient.builder()
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 지역(시/도, 시/군/구)을 지정해서 수집한다. 페이징은 내부에서 흡수. */
    public List<NmcStatusDto> fetchLatestStatuses(String stage1, String stage2) {
        final int timeout = Math.max(500, timeoutMs);
        final int pageSize = 200;
        int pageNo = 1;

        List<NmcStatusDto> acc = new ArrayList<>();
        try {
            while (true) {
                String url = UriComponentsBuilder
                        .fromHttpUrl(openApiBase + "/" + endpoint)
                        .queryParam("_type", "json")
                        // serviceKey는 미리 인코딩하고, build(true)로 재인코딩 방지
                        .queryParam("serviceKey", URLEncoder.encode(serviceKey, StandardCharsets.UTF_8))
                        .queryParam("STAGE1", stage1)
                        .queryParam("STAGE2", stage2)
                        .queryParam("pageNo", pageNo)
                        .queryParam("numOfRows", pageSize)
                        .build(true)
                        .toUriString();

                Mono<String> mono = wc().get().uri(url).retrieve().bodyToMono(String.class);
                String json = mono.block(Duration.ofMillis(timeout));
                if (json == null) break;

                JsonNode items = om.readTree(json)
                        .path("response").path("body")
                        .path("items").path("item");

                if (items.isMissingNode() || items.isNull()) break;

                if (items.isArray()) {
                    for (JsonNode it : items) acc.add(mapToDto(it));
                    if (items.size() < pageSize) break;   // 마지막 페이지
                } else {
                    acc.add(mapToDto(items));              // 단일 객체 케이스
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

    // ---------- mapping (네 DTO 필드에 맞춤) ----------
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
            if (v != null && !v.isNull()) return v.asText();
        }
        return null;
    }

    private static Integer intOrNull(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || v.isNull()) return null;
        try { return Integer.parseInt(v.asText().trim()); }
        catch (Exception ignore) { return null; }
    }

    // ---------- hvidate parsing ----------
    /** 다양한 포맷 대비: ISO-8601 우선, 아니면 KST 가정 후 UTC로 변환 */
    private static Instant parseHvidateToInstant(String s) {
        if (s == null || s.isBlank()) return null;

        // 1) ISO-8601 (Z/오프셋 포함)
        try { return Instant.parse(s.trim()); } catch (Exception ignore) { }

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
                LocalDateTime ldt = LocalDateTime.parse(s.trim(), f);
                return ldt.atZone(KST).toInstant();
            } catch (Exception ignore) { }
        }

        // 3) Zoned 문자열
        try { return ZonedDateTime.parse(s.trim()).toInstant(); } catch (Exception ignore) { }

        return null;
    }
}
