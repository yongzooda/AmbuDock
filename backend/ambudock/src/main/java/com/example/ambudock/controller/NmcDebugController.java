package com.example.ambudock.controller;

import com.example.ambudock.scheduler.client.NmcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/debug")
@Slf4j
public class NmcDebugController {

    private final NmcClient nmcClient;

    // 같은 properties에서 한 번 더 뽑아서 JDK HttpURLConnection 테스트용으로 사용
    @Value("${app.nmc.openapi.service_key}")
    private String serviceKey;

    // 컨트롤러 살아있는지 확인용
    @GetMapping("/ping")
    public String ping() {
        return "debug-ok";
    }

    /**
     * 예:
     *  GET /debug/nmc/raw?stage2=노원구
     *  GET /debug/nmc/raw?stage1=서울특별시&stage2=성북구
     *
     * NMC 클라이언트(NmcClient.fetchRawOnce)를 그대로 태워서 XML을 반환.
     */
    @GetMapping(
            value = "/nmc/raw",
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> raw(
            @RequestParam(defaultValue = "서울특별시") String stage1,
            @RequestParam String stage2
    ) {
        try {
            String xml = nmcClient.fetchRawOnce(stage1, stage2);
            return ResponseEntity.ok(xml);
        } catch (WebClientResponseException e) {
            // HTTP 4xx/5xx는 그대로 상태코드/본문을 노출
            return ResponseEntity
                    .status(e.getStatusCode())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("NMC error: " + e.getStatusCode() +
                            "\n\n" + e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity
                    .status(502)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("NMC error: " + e.toString());
        }
    }


    /**
     * 하드코딩된 URL 그대로 호출.
     * 브라우저에서 잘 되는 URL을 그대로 복붙해서 비교.
     */
    @GetMapping(
            value = "/nmc/hardcoded",
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> rawHardcoded() {
        try {
            String xml = nmcClient.fetchRawHardcoded();
            return ResponseEntity.ok(xml);
        } catch (WebClientResponseException e) {
            return ResponseEntity
                    .status(e.getStatusCode())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("NMC error: " + e.getStatusCode() +
                            "\n\n" + e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity
                    .status(502)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("NMC error: " + e.toString());
        }
    }


    /**
     * curl 스타일로 URL을 만들고, WebClient로 호출.
     * User-Agent / Accept 등을 curl 비슷하게 맞춤.
     */
    @GetMapping(
            value = "/curl-style",
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> debugCurlStyle(@RequestParam String stage2) {
        try {
            String xml = nmcClient.debugCurlStyle("서울특별시", stage2);
            return ResponseEntity.ok(xml);
        } catch (WebClientResponseException e) {
            return ResponseEntity
                    .status(e.getStatusCode())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("NMC error: " + e.getStatusCode() +
                            "\n\n" + e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity
                    .status(502)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("NMC error: " + e.toString());
        }
    }


    /**
     * ★ 추가 디버깅용: NMC에서 제공하는 자바 샘플처럼 HttpURLConnection으로 직접 호출
     * - WebClient/Netty를 완전히 우회
     * - 이게 3건 나오면 → Netty/WebClient 쪽과 NMC 서버 사이에서 무언가 다르게 동작하는 것.
     */
    @GetMapping(
            value = "/nmc/jdk",
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> jdkStyle(
            @RequestParam(defaultValue = "서울특별시") String stage1,
            @RequestParam String stage2
    ) throws Exception {

        String base = "https://apis.data.go.kr/B552657/ErmctInfoInqireService/getEmrrmRltmUsefulSckbdInfoInqire";

        // serviceKey는 지금처럼 이미 URL-safe(hex)라서 encode해도 안 해도 똑같음
        String urlStr = base
                + "?serviceKey=" + serviceKey
                + "&STAGE1=" + URLEncoder.encode(stage1, StandardCharsets.UTF_8)
                + "&STAGE2=" + URLEncoder.encode(stage2, StandardCharsets.UTF_8)
                + "&pageNo=1"
                + "&numOfRows=100";

        log.info("NMC JDK-style request URL={}", urlStr);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/xml");

        int code = conn.getResponseCode();
        log.info("NMC JDK-style responseCode={}", code);

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return ResponseEntity.ok(sb.toString());
        }
    }
}
