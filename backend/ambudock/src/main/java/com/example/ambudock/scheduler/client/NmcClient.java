package com.example.ambudock.scheduler.client;

import com.example.ambudock.dto.NmcStatusDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
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

    // ★ JSON이 아니라 XML을 파싱하기 위한 Mapper
    private final XmlMapper xmlMapper = new XmlMapper();

    private WebClient wc() {
        int t = Math.max(1500, timeoutMs);
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, t)     // 연결 타임아웃
                .responseTimeout(Duration.ofMillis(t))               // 응답 헤더 타임아웃
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(t, TimeUnit.MILLISECONDS))   // 본문 읽기
                        .addHandlerLast(new WriteTimeoutHandler(t, TimeUnit.MILLISECONDS))  // 본문 쓰기
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader("Accept", MediaType.APPLICATION_XML_VALUE)   // ★ XML
                .build();
    }

    /** 지역(시/도, 시/군/구)을 지정해서 수집한다. 페이징은 내부에서 흡수. */
    public List<NmcStatusDto> fetchLatestStatuses(String stage1, String stage2) {
        final int pageSize = 100;      // ↓ 응답을 좀 더 빨리 받기 위해 100 권장
        final int blockMs  = Math.max(3500, timeoutMs + 2000); // block용 여유 타임아웃 (3.5s 이상)
        int pageNo = 1;

        List<NmcStatusDto> acc = new ArrayList<>();
        try {
            while (true) {
                String url = UriComponentsBuilder
                        .fromHttpUrl(openApiBase)                // https://apis.data.go.kr/B552657/ErmctInfoInqireService
                        .pathSegment(endpoint.split("/"))        // getEmrrmRltmUsefulSckbdInfoInqire
                        .queryParam("serviceKey", serviceKey)    // 사전 URLEncode 금지
                        .queryParam("STAGE1", stage1)            // 한글 그대로
                        .queryParam("STAGE2", stage2)
                        .queryParam("pageNo", pageNo)
                        .queryParam("numOfRows", pageSize)
                        .encode(StandardCharsets.UTF_8)          // 마지막에 한 번만
                        .build()
                        .toUriString();

                log.info("NMC request URL={}", url);

                String xml = wc().get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_XML)       // ★ XML
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofMillis(blockMs))     // ← 리액터 레벨 타임아웃
                        .block();                                // 위 timeout이 block까지 적용

                if (xml == null) break;

                // ★ XML → JsonNode (root 태그 = <response>)
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

                // 비정상 코드면 즉시 중단 (키/파라미터 문제 조기 발견)
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
                    for (JsonNode it : items) acc.add(mapToDto(it));
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

    /**
     * 디버그용: NMC 원본 XML을 1페이지만 그대로 반환.
     * - 브라우저에서 localhost 디버그 엔드포인트와 NMC 오픈API를 동시에 호출해서
     *   totalCount가 같은지 비교하기 위한 용도.
     */
    public String fetchRawOnce(String stage1, String stage2) {
        final int pageSize = 100;
        final int blockMs  = Math.max(3500, timeoutMs + 2000);

        String url = UriComponentsBuilder
                .fromHttpUrl(openApiBase)
                .pathSegment(endpoint.split("/"))
                .queryParam("serviceKey", serviceKey)
                .queryParam("STAGE1", stage1)
                .queryParam("STAGE2", stage2)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", pageSize)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();

        log.info("NMC DEBUG raw request URL={}", url);

        String xml = wc().get()
                .uri(url)
                .accept(MediaType.APPLICATION_XML)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(blockMs))
                .block();

        log.info("NMC DEBUG raw response length={} chars", (xml != null ? xml.length() : 0));
        return xml;
    }


    /**
     * 디버그용: 브라우저에서 잘 되는 URL 문자열을 그대로 WebClient로 호출해 본다.
     */
    public String fetchRawHardcoded() {
        final int blockMs  = Math.max(3500, timeoutMs + 2000);

        String url = "https://apis.data.go.kr/B552657/ErmctInfoInqireService"
                + "/getEmrrmRltmUsefulSckbdInfoInqire"
                + "?serviceKey=8381bc45d91030765bbb38576f9c6f0a2082ff5e8a513ec2f2c773d5e1166884"
                + "&STAGE1=%EC%84%9C%EC%9A%B8%ED%8A%B9%EB%B3%84%EC%8B%9C"
                + "&STAGE2=%EB%85%B8%EC%9B%90%EA%B5%AC"
                + "&pageNo=1"
                + "&numOfRows=100";

        log.info("NMC HARDCODED request URL={}", url);

        String xml = wc().get()
                .uri(url)
                .accept(MediaType.APPLICATION_XML)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(blockMs))
                .block();

        log.info("NMC HARDCODED raw response length={} chars", (xml != null ? xml.length() : 0));
        return xml;
    }


    public String debugCurlStyle(String stage1, String stage2) {
        String url = "https://apis.data.go.kr"
                + "/B552657/ErmctInfoInqireService/getEmrrmRltmUsefulSckbdInfoInqire"
                + "?serviceKey=" + serviceKey
                + "&STAGE1=" + java.net.URLEncoder.encode(stage1, java.nio.charset.StandardCharsets.UTF_8)
                + "&STAGE2=" + java.net.URLEncoder.encode(stage2, java.nio.charset.StandardCharsets.UTF_8)
                + "&pageNo=1"
                + "&numOfRows=10";

        log.info("NMC DEBUG curl-style URL={}", url);

        WebClient client = WebClient.builder()
                .defaultHeader("User-Agent", "curl/8.7.1")
                .defaultHeader("Accept", "*/*")
                .build();

        String xml = client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("NMC DEBUG curl-style raw length={}", (xml != null ? xml.length() : -1));
        return xml;
    }


}
