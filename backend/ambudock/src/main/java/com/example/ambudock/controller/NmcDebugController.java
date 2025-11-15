package com.example.ambudock.controller;

import com.example.ambudock.scheduler.client.NmcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * NMC 관련 디버그 엔드포인트.
 * - /debug/ping : 컨트롤러 생존 확인
 * - /debug/nmc/raw : NmcClient.fetchRawOnce(stage1, stage2)를 그대로 태워서 원본 XML 확인
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/debug")
@Slf4j
public class NmcDebugController {

    private final NmcClient nmcClient;

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
     * NmcClient.fetchRawOnce(stage1, stage2)를 그대로 호출해서
     * NMC 원본 XML 한 페이지를 반환한다.
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

            if (xml == null || xml.isBlank()) {
                log.warn("NMC DEBUG raw: empty response. stage1={}, stage2={}", stage1, stage2);
                return ResponseEntity
                        .status(502)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("NMC empty response (null/blank)");
            }

            return ResponseEntity.ok(xml);
        } catch (Exception e) {
            log.warn("NMC DEBUG raw failed: stage1={}, stage2={}, cause={}",
                    stage1, stage2, e.toString());

            return ResponseEntity
                    .status(502)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("NMC error: " + e.toString());
        }
    }
}
