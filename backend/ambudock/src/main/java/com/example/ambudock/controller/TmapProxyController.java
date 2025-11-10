package com.example.ambudock.controller;

import com.example.ambudock.dto.tmap.TmapDtos;
import com.example.ambudock.service.TmapProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/tmap", produces = MediaType.APPLICATION_JSON_VALUE)

// 프론트엔드 → 백엔드 → SK TMAP Proxy 호출을 중계하는 진입점 컨트롤러
public class TmapProxyController {

    private final TmapProxyService tmap;

    /**
     * 후보 K(≤10) 병원에 대해 차량/도보 ETA와 직선거리(line_km)를 계산.
     *
     * 엔드포인트: POST /api/tmap/route
     *
     * 동작 흐름:
     *  - 요청 바디를 DTO로 바인딩 및 검증(@Valid)
     *  - 후보 개수(K) 안전 검사 (K <= 10)
     *  - 클라이언트 IP 추출 (X-Forwarded-For 우선 -> fallback remoteAddr)
     *  - TmapProxyService 호출 (실제 TMAP 프록시/캐시/타임아웃/대체 로직)
     *  - 결과를 HTTP 200으로 반환
     *
     */
    @PostMapping(path = "/route", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TmapDtos.RouteResponse> routeForCandidates(
            @RequestBody @Valid TmapDtos.RouteRequest req,
            HttpServletRequest http
    ) {
        // ---------- 안전 가드: 후보 개수 제한 ----------
        // 컨트롤러 레벨에서 K 제한을 한 번 더 확인.
        if (req.getCandidates().size() > 10) {
            // 요청이 허용 범위를 초과했을 때 명시적 에러 반환.
            return ResponseEntity.badRequest()
                    .body(new TmapDtos.RouteResponse(
                            null, 0L, "BAD_REQUEST:K>10", 0
                    ));
        }
        // ---------- 클라이언트 IP 추출 ----------
        // 로깅 / 트래픽 제어 / 장애 분석 목적으로 IP 전달.
        String clientIp = extractClientIp(http);

        // ---------- 실제 서비스 호출(비즈니스 로직 위임) ----------
        // TmapDtos.RouteResponse가 res에 채워짐
        var res = tmap.routeForCandidates(req, clientIp);

        // 정상 응답 반환 (HTTP 200 + JSON)
        return ResponseEntity.ok(res);
    }

    /** 헬스체크용 핑 엔드포인트: GET /api/tmap/ping → "tmap-ok" */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() { return ResponseEntity.ok("tmap-ok"); }

    /* ---------------- 내부 유틸 ---------------- */

    // 요청자의 IP 추출 유틸
    // 로드밸런서가 있으면 X-Forwarded-For의 첫 번째 값 사용
    // 없으면 기본 remoteAddr 사용
    private String extractClientIp(HttpServletRequest request) {
        String h = request.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    /**
     * 요청 바디의 검증(예: @Valid)에서 에러가 날 때 처리하는 핸들러
     * - 검사 실패가 발생하면 HTTP 400(Bad Request)을 반환하고,
     *   응답 바디에는 간단한 에러 코드와 첫 번째 검증 오류 메시지를 담음.
     */
    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<TmapDtos.ErrorBody> onBindError(Exception ex) {
        String msg = "invalid request";
        if (ex instanceof BindException be && !be.getAllErrors().isEmpty()) {
            msg = be.getAllErrors().get(0).getDefaultMessage();
        } else if (ex instanceof MethodArgumentNotValidException me && !me.getBindingResult().getAllErrors().isEmpty()) {
            msg = me.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(new TmapDtos.ErrorBody("BAD_REQUEST", msg));
    }
}
