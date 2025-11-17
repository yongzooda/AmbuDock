package com.example.ambudock.controller;

import com.example.ambudock.domain.hospital.HliSnapshot;
import com.example.ambudock.domain.hospital.Hospital;
import com.example.ambudock.domain.hospital.HospitalStatus;
import com.example.ambudock.dto.hospital.HospitalDtos.HliSummary;
import com.example.ambudock.dto.hospital.HospitalDtos;
import com.example.ambudock.dto.disaster.DisasterDtos;
import com.example.ambudock.repository.HliSnapshotRepository;
import com.example.ambudock.repository.HospitalRepository;
import com.example.ambudock.repository.HospitalStatusRepository;
import com.example.ambudock.service.AppConfigService;
import com.example.ambudock.service.HliService;
import com.example.ambudock.service.HospitalQueryService;
import com.example.ambudock.service.DisasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hospitals")
public class HospitalController {

    private final HospitalQueryService listService;
    private final AppConfigService cfg;

    // for detail
    private final HospitalRepository hospitalRepo;
    private final HospitalStatusRepository statusRepo;
    private final HliSnapshotRepository hliRepo;
    private final HliService hliService;
    private final DisasterService disasterService;

    /**
     * 기존 목록 조회 (unchanged)
     * GET /api/hospitals?center=lat,lon&radius_km=
     */
    @GetMapping
    public HospitalDtos.NearbyResponse list(@RequestParam String center,
                                            @RequestParam(name = "radius_km", required = false) Double radiusKm) {
        String[] parts = center.split(",");
        double lat = Double.parseDouble(parts[0].trim()), lon = Double.parseDouble(parts[1].trim());
        if (radiusKm == null) radiusKm = (double) cfg.getInt("default-radius-km", 15);
        return listService.listNearby(lat, lon, radiusKm);
    }

    /**
     * 병원 상세 하프시트
     * GET /api/hospitals/{hpid}
     *
     * 응답 몸체는 Map<String,Object> 형태로 직렬화되어 프론트에 전달됩니다.
     * - 기본 메타: hpid, name, lat, lon, tel_emergency, addr_road
     * - 최신 지표: hvec, hv2..hv6, hvoc  (있으면 숫자, 없으면 null)
     * - HLI 요약: hli (HliSummary DTO) 또는 null
     * - badges/severity: 재난문자 기반 WARN/BLOCK 정보 (DisasterService 결과)
     */
    @GetMapping("/{hpid}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String hpid) {

        Optional<Hospital> oh = hospitalRepo.findById(hpid);
        if (oh.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Hospital h = oh.get();

        // 최신 HospitalStatus 조회 (페이지 1)
        HospitalStatus latest = statusRepo.findLatestByHpid(
                h.getHpid(),
                org.springframework.data.domain.PageRequest.of(0, 1)
        ).stream().findFirst().orElse(null);

        // HLI: 우선 스냅샷, 없으면 즉석 계산(compute), 없으면 null
        HliSummary hliSummary = null;
        if (latest != null) {
            HliSnapshot snap = hliRepo.findByStatus_StatusId(latest.getStatusId()).orElse(null);
            if (snap != null) {
                // snap.getCalcAt() 는 Instant 로 가정
                hliSummary = HliSummary.builder()
                        .score(snap.getScore())
                        .level(snap.getLevel().name())
                        .color(colorOf(snap.getLevel()))
                        .lagSec(snap.getLagSec())
                        .staleLevel(snap.getStaleLevel().name())
                        .updatedAt(snap.getCalcAt())    // Instant
                        .build();
            } else {
                // 즉석 계산 (AppConfig에서 기준값 사용)
                int soft = cfg.getInt("STALE_SOFT_SEC", 300);
                int hard = cfg.getInt("STALE_HARD_SEC", 3600);
                int penalty = cfg.getInt("HLI_LAG_SOFT_PENALTY", 15);
                var comp = hliService.compute(latest, soft, hard, penalty);

                // comp.getComputedAt() 는 Instant 로 가정
                Instant computedAt = comp.getComputedAt();

                hliSummary = HliSummary.builder()
                        .score(comp.getScore())
                        .level(comp.getLevel().name())
                        .color(colorOf(comp.getLevel()))
                        .lagSec(comp.getLagSec())
                        .staleLevel(comp.getStaleLevel().name())
                        .updatedAt(computedAt)   // Instant
                        .build();
            }
        }

        // 최신 hv* 값 (nullable 안전 추출)
        Integer hvec = latest == null ? null : latest.getHvec();
        Integer hv2  = latest == null ? null : latest.getHv2();
        Integer hv3  = latest == null ? null : latest.getHv3();
        Integer hv4  = latest == null ? null : latest.getHv4();
        Integer hv5  = latest == null ? null : latest.getHv5();
        Integer hv6  = latest == null ? null : latest.getHv6();
        Integer hvoc = latest == null ? null : latest.getHvoc();

        // 재난문자 평가 (DisasterService.evaluate expects List<String>)
        DisasterDtos.EvaluateResponse eval = disasterService.evaluate(List.of(h.getHpid()));
        DisasterDtos.EvaluateItem item = eval.getResults() != null && !eval.getResults().isEmpty()
                ? eval.getResults().get(0) : null;

        String severity = (item == null) ? null : item.getSeverity();
        boolean blocked = (item != null && item.isBlocked());
        List<String> badges = (item == null || item.getBadges() == null) ? List.of() : item.getBadges();

        // ✅ 최종 응답 body: null 허용 Map 사용 (Map.ofEntries는 value null 불가라 NPE 발생)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hpid", h.getHpid());
        body.put("name", h.getName());
        body.put("lat", h.getLat());
        body.put("lon", h.getLon());
        body.put("tel_emergency", h.getTelEmergency());
        body.put("addr_road", h.getAddrRoad());

        // 최신 지표 (없으면 null 그대로 내려감)
        body.put("hvec", hvec);
        body.put("hv2", hv2);
        body.put("hv3", hv3);
        body.put("hv4", hv4);
        body.put("hv5", hv5);
        body.put("hv6", hv6);
        body.put("hvoc", hvoc);

        // HLI 요약 (없으면 null)
        body.put("hli", hliSummary);

        // 재난 관련
        body.put("severity", severity);
        body.put("blocked", blocked);
        body.put("badges", badges);

        return ResponseEntity.ok(body);
    }

    // 헬퍼: HliLevel -> 컬러(hex)
    private String colorOf(com.example.ambudock.domain.common.enums.HliLevel lvl) {
        return switch (lvl) {
            case GREEN -> "#16a34a";
            case YELLOW -> "#eab308";
            case RED -> "#ef4444";
            case GRAY -> "#9ca3af";
        };
    }
}
