package com.example.ambudock.controller;

import com.example.ambudock.dto.recommendation.RecommendationDtos;
import com.example.ambudock.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Top-N 응급실 추천 컨트롤러
 * - 입력: center("lat,lon"), radius_km(Optional), topn(Optional)
 * - 처리: RecommendationService에서 후보 K 확보 → HLI 1차 → 재난문자 2차 → TMAP Proxy(전 후보) → 정렬 → TopN
 * - 출력: RecommendationDtos.TopResponse (TopItem 카드들)
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping(value = "/api/recommend", produces = MediaType.APPLICATION_JSON_VALUE)
public class RecommendationController {

    private final RecommendationService service;

    @PostMapping(value = "/top", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecommendationDtos.TopResponse> top(
            @RequestBody @Valid TopRequest req,
            HttpServletRequest http
    ) {
        String clientIp = extractClientIp(http);
        RecommendationDtos.TopResponse res = service.recommendTop(req.getCenter(), req.getRadius_km(), req.getTopn(), clientIp);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("recommend-ok");
    }

    // ----------------- helpers -----------------

    private String extractClientIp(HttpServletRequest request) {
        String h = request.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    // ----------------- request dto -----------------

    /**
     * 요청 바디 DTO
     * center: "lat,lon" (반드시 공백 없이 소수)
     * radius_km: 없으면 AppConfig.default-radius-km 사용
     * topn: 없으면 AppConfig.RANK_TOPN 사용(기본 3)
     */
    @Getter
    @NoArgsConstructor   // ✅ Jackson 역직렬화용 기본 생성자
    public static class TopRequest {
        @NotBlank
        private String center;      // 예: "37.5665,126.9780"
        private Double radius_km;   // optional
        private Integer topn;       // optional
    }
}
