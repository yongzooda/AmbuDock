package com.example.ambudock.controller;

import com.example.ambudock.dto.disaster.DisasterDtos;
import com.example.ambudock.service.DisasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * DisasterController
 *
 * 역할
 *  - 재난 문자 기반 2차 필터 공개 API 제공
 *    - POST /api/disasters/evaluate : 병원 HPID 목록에 대해 BLOCK 제외/배지/심각도 평가
 *
 *  - (선택) 운영 편의 API
 *    - POST /api/disasters/ingest : MOIS 최신 재난문자 수집/정규화/심각도 판정/TTL/지역 매핑/만료 수행 트리거
 *
 * 응답 규격
 *  - evaluate : DisasterDtos.EvaluateResponse (results[ { hpid, blocked, badges[], severity } ])
 *
 * 주의
 *  - 실데이터 최신성은 스케줄러(A4) 또는 ingest 엔드포인트에 의해 유지된다.
 *  - 컨트롤러는 얇게 유지하고, 모든 도메인 로직은 DisasterService에 집중한다.
 */
@RestController
@RequestMapping("/api/disasters")
@RequiredArgsConstructor
@Validated
@Slf4j
public class DisasterController {

    private final DisasterService disasterService;

    /**
     * 병원 HPID 배열에 대해 재난 문자 2차 필터 평가 수행
     *
     * Request:
     *  POST /api/disasters/evaluate
     *  {
     *    "hpids": ["A1100016","A1100021","A1100033"]
     *  }
     *
     * Response:
     *  200 OK
     *  {
     *    "results": [
     *      { "hpid":"A1100016", "blocked": false, "badges": ["이동차질 가능(서울특별시 강남구)"], "severity": "WARN" },
     *      { "hpid":"A1100021", "blocked": true,  "badges": [],                                        "severity": "BLOCK" },
     *      { "hpid":"A1100033", "blocked": false, "badges": [],                                        "severity": null }
     *    ]
     *  }
     */
    @PostMapping("/evaluate")
    public ResponseEntity<DisasterDtos.EvaluateResponse> evaluate(
            @Valid @RequestBody DisasterDtos.EvaluateRequest body
    ) {
        var res = disasterService.evaluate(body.getHpids());
        return ResponseEntity.ok(res);
    }

    /**
     * (선택/운영용) MOIS 최신 재난문자 수집+정규화+심각도 판정+TTL+지역 매핑+만료 처리 트리거
     * - 스케줄러(A4)가 자동 수행하지만, 운영 중 수동으로 즉시 갱신이 필요할 때 호출
     *
     * Request:
     *  POST /api/disasters/ingest
     *
     * Response:
     *  200 OK
     *  {
     *    "fetched": 23,
     *    "inserted": 7,
     *    "updated": 9,
     *    "expired": 3,
     *    "linked": 21
     *  }
     */
    @PostMapping("/ingest")
    public ResponseEntity<DisasterService.IngestReport> ingestNow() {
        var report = disasterService.ingestLatestFromMois();
        return ResponseEntity.ok(report);
    }
}
