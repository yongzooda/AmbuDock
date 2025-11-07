package com.example.ambudock.scheduler;

import com.example.ambudock.dto.MoisAlertDto;
import com.example.ambudock.dto.NmcStatusDto;
import com.example.ambudock.domain.disaster.DisasterAlert;
import com.example.ambudock.domain.hospital.HliSnapshot;
import com.example.ambudock.domain.hospital.HospitalStatus;
import com.example.ambudock.repository.DisasterAlertRepository;
import com.example.ambudock.repository.HliSnapshotRepository;
import com.example.ambudock.repository.HospitalRepository;
import com.example.ambudock.repository.HospitalStatusRepository;
import com.example.ambudock.scheduler.client.MoisClient;
import com.example.ambudock.scheduler.client.NmcClient;
import com.example.ambudock.service.AppConfigService;
import com.example.ambudock.service.HliService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class Schedulers {

    private final NmcClient nmcClient;
    private final MoisClient moisClient;

    private final HospitalRepository hospitalRepo;
    private final HospitalStatusRepository statusRepo;
    private final HliSnapshotRepository hliRepo;
    private final DisasterAlertRepository disasterRepo;

    private final HliService hliService;
    private final AppConfigService cfg;

    private final EntityManager em; // md101Sn 등 조회/merge 용도

    // ---------- A2: NMC 상태 수집 (60s) ----------
    @Scheduled(fixedDelayString = "${sched.a2.delay:60000}", initialDelayString = "${sched.a2.initial:15000}")
    @Transactional
    public void a2_loadNmcStatus() {
        log.debug("A2: start NMC status load");
        List<NmcStatusDto> list;
        try {
            list = nmcClient.fetchLatestStatuses();
        } catch (Exception e) {
            log.warn("A2: nmcClient.fetchLatestStatuses failed: {}", e.toString());
            return;
        }

        int processed = 0, skipped = 0;
        for (NmcStatusDto dto : list) {
            if (dto == null || dto.getHpid() == null) {
                skipped++;
                continue;
            }

            String hpid = dto.getHpid();
            if (!hospitalRepo.existsById(hpid)) {
                log.debug("A2: unknown hospital hpid={}, skip (consider onboarding)", hpid);
                skipped++;
                continue;
            }

            // 가장 최신 상태 1건 조회
            Optional<HospitalStatus> latestOpt = statusRepo.findLatestByHpid(hpid, PageRequest.of(0, 1))
                    .stream().findFirst();

            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            if (latestOpt.isPresent() && dto.getHvidate() != null &&
                    dto.getHvidate().equals(latestOpt.get().getHvidate())) {
                // 같은 hvidate인 경우 기존 레코드 업데이트 (ingestedAt, 수치들)
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
                // 새 레코드 삽입
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
        log.info("A2: finished NMC load: processed={}, skipped={}", processed, skipped);
    }

    // ---------- A3: HLI 계산 (60s) ----------
    @Scheduled(fixedDelayString = "${sched.a3.delay:60000}", initialDelayString = "${sched.a3.initial:30000}")
    @Transactional
    public void a3_computeHli() {
        log.debug("A3: start HLI compute");

        // AppConfig에서 HLI 파라미터 읽기 (기본값 보장)
        int soft = cfg.getInt("STALE_SOFT_SEC", 300);
        int hard = cfg.getInt("STALE_HARD_SEC", 3600);
        int penalty = cfg.getInt("HLI_LAG_SOFT_PENALTY", 15);

        // 운영에서는 변경된 상태만(또는 최근 N시간치) 페이징으로 처리하는 것이 권장됨.
        List<HospitalStatus> statuses = statusRepo.findAll();

        int updated = 0;
        for (HospitalStatus s : statuses) {
            try {
                var comp = hliService.compute(s, soft, hard, penalty);

                // snapshot upsert (statusId 기준)
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

    // ---------- A4: MOIS 수집/정규화 및 TTL 만료 처리 (180s) ----------
    @Scheduled(fixedDelayString = "${sched.a4.delay:180000}", initialDelayString = "${sched.a4.initial:10000}")
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

        int inserted = 0, updated = 0;
        for (MoisAlertDto dto : alerts) {
            if (dto == null || dto.getMd101Sn() == null) continue;

            // md101Sn 기준으로 기존 엔티티 검색 (EntityManager 사용 — repo에 findByMd101Sn가 없을 수 있으므로)
            TypedQuery<DisasterAlert> q = em.createQuery(
                    "select a from DisasterAlert a where a.md101Sn = :sn", DisasterAlert.class);
            q.setParameter("sn", dto.getMd101Sn());
            List<DisasterAlert> found = q.getResultList();

            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            if (found.isEmpty()) {
                // insert
                DisasterAlert a = new DisasterAlert();
                a.setMd101Sn(dto.getMd101Sn());
                a.setIssuedAt(dto.getIssuedAt() == null ? now : dto.getIssuedAt());
                a.setLocationId(dto.getLocationId());
                a.setLocationName(dto.getLocationName());
                a.setMsg(dto.getMsg());
                a.setSeverity(dto.getSeverity() == null ? null : dto.getSeverity());
                a.setExpiresAt(dto.getExpiresAt());
                a.setIsActive(true);
                em.persist(a);
                inserted++;
            } else {
                // update existing (마지막으로 덮어쓰기)
                DisasterAlert ex = found.get(0);
                ex.setIssuedAt(dto.getIssuedAt() == null ? ex.getIssuedAt() : dto.getIssuedAt());
                ex.setLocationId(dto.getLocationId());
                ex.setLocationName(dto.getLocationName());
                ex.setMsg(dto.getMsg());
                ex.setSeverity(dto.getSeverity() == null ? ex.getSeverity() : dto.getSeverity());
                ex.setExpiresAt(dto.getExpiresAt());
                ex.setIsActive(true);
                em.merge(ex);
                updated++;
            }
        }

        // TTL 만료 처리: active한 alert들 중 expiresAt 지난 것은 isActive=false로 변경
        List<DisasterAlert> active = disasterRepo.findActiveAlerts();
        int expired = 0;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for (DisasterAlert a : active) {
            if (a.getExpiresAt() != null && a.getExpiresAt().isBefore(now)) {
                a.setIsActive(false);
                disasterRepo.save(a);
                expired++;
            }
        }

        log.info("A4: processed alerts inserted={}, updated={}, expired={}", inserted, updated, expired);
    }
}
