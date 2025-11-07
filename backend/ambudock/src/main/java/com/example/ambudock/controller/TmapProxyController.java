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

//프론트엔드 → 백엔드 → SK TMAP Proxy 호출을 중계하는 진입점.
public class TmapProxyController {

    private final TmapProxyService tmap;

    /**
     * 후보 K(≤10) 병원에 대해 차량/도보 ETA와 직선거리(line_km)를 계산.
     * - 입력: origin("lat,lon") + candidates[hpid, lat, lon] + options(차량/도보)
     * - 출력: 병원별 ETA/route_km/line_km/source("TMAP"|"ESTIMATE")
     * - 정책: 캐시 2분, 타임아웃 1.5s, 실패 시 “ESTIMATE”
     */
    @PostMapping(path = "/route", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TmapDtos.RouteResponse> routeForCandidates(
            @RequestBody @Valid TmapDtos.RouteRequest req,
            HttpServletRequest http
    ) {
        // 안전 가드: 후보 병원 개수 제한(K ≤ 10)
        if (req.getCandidates().size() > 10) {
            return ResponseEntity.badRequest()
                    .body(new TmapDtos.RouteResponse(
                            null, 0L, "BAD_REQUEST:K>10", 0
                    ));
        }
        // 요청자의 IP 추출 (X-Forwarded-For 헤더 → fallback: remoteAddr)
        String clientIp = extractClientIp(http);
        // 실제 TMAP Proxy 서비스 호출 (DB 설정값 기반 WebClient)
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
