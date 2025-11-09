package com.example.ambudock.scheduler;

import com.example.ambudock.dto.MoisAlertDto;
import com.example.ambudock.dto.NemcStatusDto;
import com.example.ambudock.domain.disaster.DisasterAlert;
import com.example.ambudock.domain.hospital.HliSnapshot;
import com.example.ambudock.domain.hospital.HospitalStatus;
import com.example.ambudock.domain.common.enums.Severity;
import com.example.ambudock.repository.DisasterAlertRepository;
import com.example.ambudock.repository.HliSnapshotRepository;
import com.example.ambudock.repository.HospitalRepository;
import com.example.ambudock.repository.HospitalStatusRepository;
import com.example.ambudock.scheduler.client.MoisClient;
import com.example.ambudock.scheduler.client.NemcClient;
import com.example.ambudock.service.AppConfigService;
import com.example.ambudock.service.HliService;
import com.example.ambudock.service.HospitalOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Schedulers
 *
 * - A2: NEMC(병원 실시간 상태) 수집 (주기: 60s, 초기 지연 15s 기본)
 * - A3: HLI 계산 (주기: 60s, 초기 지연 30s 기본)
 * - A4: MOIS 재난문자 수집 및 TTL 만료 처리 (주기: 180s, 초기 지연 10s 기본)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Schedulers {

    // 외부 API 클라이언트들
    private final NemcClient nemcClient;
    private final MoisClient moisClient;

    private final HospitalRepository hospitalRepo;
    private final HospitalStatusRepository statusRepo;
    private final HliSnapshotRepository hliRepo;
    private final DisasterAlertRepository disasterRepo;

    private final HliService hliService;
    private final AppConfigService cfg;

    // JPA
    private final EntityManager em; // md101Sn/SN 조회용

    // 온보딩 서비스
    private final HospitalOnboardingService onboardingService;

    // ---------- A2: NEMC 상태 수집 ----------
    @Scheduled(
            fixedDelayString = "${scheduler.nemc_fetch.fixed_delay_ms:60000}",
            initialDelayString = "${scheduler.nemc_fetch.initial_delay_ms:15000}"
    )
    @Transactional
    public void a2_loadNemcStatus() {
        log.debug("A2: start NEMC status load");

        List<RegionPair> regions = resolveNemcRegions();
        int regionCnt = regions.size();
        int processed = 0, skipped = 0;

        for (RegionPair rp : regions) {
            List<NemcStatusDto> list;
            try {
                list = nemcClient.fetchLatestStatuses(rp.sido(), rp.sigungu());
            } catch (Exception e) {
                log.warn("A2: nemcClient.fetchLatestStatuses failed for {} {}: {}", rp.sido(), rp.sigungu(), e.toString());
                continue;
            }

            for (NemcStatusDto dto : list) {
                if (dto == null || dto.getHpid() == null) {
                    skipped++;
                    continue;
                }

                String hpid = dto.getHpid();

                // 온보딩(없으면 생성, 있으면 메타 보정)
                if (!hospitalRepo.existsById(hpid)) {
                    boolean ok = onboardingService.ensureHospitalAndRegion(hpid, dto.getDutyname(), dto.getDutytel3());
                    if (!ok) {
                        log.debug("A2: onboarding failed for hpid={}, skip status", hpid);
                        skipped++;
                        continue;
                    }
                } else {
                    onboardingService.ensureHospitalAndRegion(hpid, dto.getDutyname(), dto.getDutytel3());
                }

                // 최신 1건
                Optional<HospitalStatus> latestOpt = statusRepo.findLatestByHpid(hpid, PageRequest.of(0, 1))
                        .stream().findFirst();

                Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

                if (latestOpt.isPresent()
                        && dto.getHvidate() != null
                        && dto.getHvidate().equals(latestOpt.get().getHvidate())) {
                    HospitalStatus ex = latestOpt.get();
                    ex.setHvec(dto.getHvec());
                    ex.setHv2(dto.getHv2());
                    ex.setHv3(dto.getHv3());
                    ex.setHv4(dto.getHv4());
                    ex.setHv5(dto.getHv5());
                    ex.setHv6(dto.getHv6());
                    ex.setHvoc(dto.getHvoc());
                    ex.setIngestedAt(now);
                    statusRepo.save(ex);
                } else {
                    HospitalStatus s = new HospitalStatus();
                    s.setHospital(hospitalRepo.findById(hpid).orElse(null));
                    s.setHvidate(dto.getHvidate() == null ? now : dto.getHvidate());
                    s.setHvec(dto.getHvec());
                    s.setHv2(dto.getHv2());
                    s.setHv3(dto.getHv3());
                    s.setHv4(dto.getHv4());
                    s.setHv5(dto.getHv5());
                    s.setHv6(dto.getHv6());
                    s.setHvoc(dto.getHvoc());
                    s.setIngestedAt(now);
                    statusRepo.save(s);
                }
                processed++;
            }
        }
        log.info("A2: finished NEMC load: regions={}, processed={}, skipped={}", regionCnt, processed, skipped);
    }

    // ---------- A3: HLI 계산 ----------
    @Scheduled(
            fixedDelayString = "${scheduler.hli_compute.fixed_delay_ms:60000}",
            initialDelayString = "${scheduler.hli_compute.initial_delay_ms:30000}"
    )
    @Transactional
    public void a3_computeHli() {
        log.debug("A3: start HLI compute");

        int soft = cfg.getInt("STALE_SOFT_SEC", 300);
        int hard = cfg.getInt("STALE_HARD_SEC", 3600);
        int penalty = cfg.getInt("HLI_LAG_SOFT_PENALTY", 15);

        List<HospitalStatus> statuses = statusRepo.findAll();

        int updated = 0;
        for (HospitalStatus s : statuses) {
            try {
                var comp = hliService.compute(s, soft, hard, penalty);

                HliSnapshot snap = hliRepo.findByStatus_StatusId(s.getStatusId()).orElse(null);
                if (snap == null) {
                    snap = new HliSnapshot();
                    snap.setStatus(s);
                    snap.setHospital(s.getHospital());
                }
                snap.setCalcAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
                snap.setErBaseScore(comp.getErBaseScore());
                snap.setIcuAvail(comp.getIcuAvail());
                snap.setIcuBucket(comp.getIcuBucket());
                snap.setIcuAdjust(comp.getIcuAdjust());
                snap.setLagSec(comp.getLagSec());
                snap.setStaleLevel(comp.getStaleLevel());
                snap.setScore(comp.getScore());
                snap.setLevel(comp.getLevel());
                snap.setReason(comp.getReasonJson());

                hliRepo.save(snap);
                updated++;
            } catch (Exception e) {
                log.warn("A3: compute failed for statusId={} cause={}", s.getStatusId(), e.toString());
            }
        }

        log.info("A3: finished HLI compute, processedStatuses={}, snapshotsUpdated={}", statuses.size(), updated);
    }

    // ---------- A4: MOIS 수집/정규화 및 TTL 만료 처리 ----------
    /**
     * - 신구 스펙 호환: md101Sn(구) 또는 SN(신) 사용
     * - severity 없으면 단계명/본문으로 유추
     * - expiresAt 없으면 AppConfig TTL 규칙으로 계산 (BLOCK 4h / WARN 2h / IGNORE 1h 기본)
     * - DisasterAlert NOT NULL 컬럼은 기본값으로 보정
     */
    @Scheduled(
            fixedDelayString = "${scheduler.mois_sync.fixed_delay_ms:180000}",
            initialDelayString = "${scheduler.mois_sync.initial_delay_ms:10000}"
    )
    @Transactional
    public void a4_loadMoisAndExpire() {
        log.debug("A4: start MOIS load & expire");

        List<MoisAlertDto> alerts;
        try {
            alerts = moisClient.fetchLatestAlerts();
        } catch (Exception e) {
            log.warn("A4: moisClient.fetchLatestAlerts failed: {}", e.toString());
            return;
        }

        long ttlBlockMin = cfg.getInt("MOIS_TTL_BLOCK_MIN", 240);
        long ttlWarnMin  = cfg.getInt("MOIS_TTL_WARN_MIN", 120);
        long ttlIgnMin   = cfg.getInt("MOIS_TTL_IGNORE_MIN", 60);

        int inserted = 0, updated = 0;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        for (MoisAlertDto dto : alerts) {
            if (dto == null) continue;

            // 1) 키: md101Sn 우선, 없으면 SN(신 스펙)
            String key = firstNonBlank(dto.getMd101Sn(), invokeString(dto, "getSn"));
            if (key == null) {
                log.debug("A4: skip alert without key (md101Sn/SN missing)");
                continue;
            }

            // 2) 필드 정규화(구/신 스펙)
            String msg = firstNonBlank(dto.getMsg(),
                    firstNonBlank(invokeString(dto, "getMessage"), invokeString(dto, "getMsgCn")));
            String locationName = firstNonBlank(dto.getLocationName(),
                    firstNonBlank(invokeString(dto, "getRegionName"), invokeString(dto, "getRcptnRgnNm")));
            String locationId = dto.getLocationId(); // 신 스펙엔 없을 수 있음

            // NOT NULL 컬럼 보정 + 길이 제한
            msg = notNull(msg, "(빈 메시지)");
            locationName = clipLen(notNull(locationName, "미상"), 120);
            locationId = clipLen(notNull(locationId, "UNKNOWN"), 40);

            Instant issuedAt = dto.getIssuedAt() == null ? now : dto.getIssuedAt();

            Severity sev = dto.getSeverity(); // 같은 enum 패키지 사용 가정
            if (sev == null) {
                String stepName = firstNonBlank(invokeString(dto, "getEmergencyStepName"), invokeString(dto, "getEmrgStepNm"));
                sev = deriveSeverity(stepName, msg); // 기본 규칙으로 유추
            }

            Instant expiresAt = dto.getExpiresAt();
            if (expiresAt == null) {
                long mins = switch (sev) {
                    case BLOCK -> ttlBlockMin;
                    case WARN  -> ttlWarnMin;
                    default    -> ttlIgnMin;
                };
                expiresAt = issuedAt.plus(mins, ChronoUnit.MINUTES);
            }

            // 3) upsert
            List<DisasterAlert> found = em.createQuery(
                            "select a from DisasterAlert a where a.md101Sn = :sn", DisasterAlert.class)
                    .setParameter("sn", key)
                    .getResultList();

            if (found.isEmpty()) {
                DisasterAlert a = new DisasterAlert();
                a.setMd101Sn(key);
                a.setIssuedAt(issuedAt);
                a.setLocationId(locationId);
                a.setLocationName(locationName);
                a.setMsg(msg);
                a.setSeverity(sev == null ? Severity.IGNORE : sev); // NOT NULL
                a.setExpiresAt(expiresAt);
                a.setIsActive(true);
                em.persist(a);
                inserted++;
            } else {
                DisasterAlert ex = found.get(0);
                ex.setIssuedAt(ex.getIssuedAt() == null ? issuedAt : ex.getIssuedAt());
                ex.setLocationId(locationId != null ? locationId : ex.getLocationId());
                ex.setLocationName(locationName != null ? locationName : ex.getLocationName());
                ex.setMsg(msg != null ? msg : ex.getMsg());
                ex.setSeverity(sev != null ? sev : ex.getSeverity());
                if (ex.getExpiresAt() == null || ex.getExpiresAt().isBefore(expiresAt)) {
                    ex.setExpiresAt(expiresAt);
                }
                ex.setIsActive(true);
                disasterRepo.save(ex);
                updated++;
            }
        }

        // 4) TTL 만료
        List<DisasterAlert> active = disasterRepo.findActiveAlerts();
        int expired = 0;
        Instant now2 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for (DisasterAlert a : active) {
            if (a.getExpiresAt() != null && a.getExpiresAt().isBefore(now2)) {
                a.setIsActive(false);
                disasterRepo.save(a);
                expired++;
            }
        }

        log.info("A4: processed alerts inserted={}, updated={}, expired={}", inserted, updated, expired);
    }

    // ---------- 지역 해석 헬퍼 ----------
    private List<RegionPair> resolveNemcRegions() {
        List<Object[]> rows = em.createQuery(
                        "select distinct r.sido, r.sigungu from Hospital h join h.region r", Object[].class)
                .getResultList();

        List<RegionPair> out = new ArrayList<>();
        for (Object[] row : rows) {
            String sido = (String) row[0];
            String sigungu = (String) row[1];
            if (sido != null && sigungu != null) {
                out.add(new RegionPair(sido, sigungu));
            }
        }

        if (out.isEmpty()) {
            out.add(new RegionPair("서울특별시", "강남구")); // 초기 fallback
        }
        return out;
    }

    private record RegionPair(String sido, String sigungu) {}

    /* ===================== A4 유틸 ===================== */

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : ((b != null && !b.isBlank()) ? b : null);
    }

    private static String notNull(String s, String fallback) {
        return (s == null) ? fallback : s;
    }

    private static String clipLen(String s, int limit) {
        if (s == null) return null;
        return (s.length() <= limit) ? s : s.substring(0, limit);
        // 필요하면 말줄임표 처리 가능: s.substring(0, Math.max(0, limit-1)) + "…"
    }

    /** 리플렉션으로 문자열 게터 호출 (없으면 null) */
    private static String invokeString(Object target, String methodName) {
        if (target == null || methodName == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            Object v = m.invoke(target);
            return v == null ? null : v.toString();
        } catch (Exception ignore) {
            return null;
        }
    }

    /** EMRG_STEP_NM/본문 키워드 기반 간단 심각도 유추 */
    private Severity deriveSeverity(String stepName, String msg) {
        String s = stepName == null ? "" : stepName.toLowerCase();
        String m = msg == null ? "" : msg.toLowerCase();

        // 단계명 우선
        if (s.contains("경보") || s.contains("대피") || s.contains("통제")) return Severity.BLOCK;
        if (s.contains("주의") || s.contains("주의보")) return Severity.WARN;

        // 본문 키워드 보정
        if (m.contains("대피") || m.contains("전면 통제") || m.contains("통제")) return Severity.BLOCK;
        if (m.contains("주의") || m.contains("강풍") || m.contains("호우") || m.contains("주의보")) return Severity.WARN;

        return Severity.IGNORE;
    }
}
