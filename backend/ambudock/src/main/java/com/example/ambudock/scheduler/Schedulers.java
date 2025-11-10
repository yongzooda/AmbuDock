package com.example.ambudock.scheduler;

import com.example.ambudock.dto.NmcStatusDto;
import com.example.ambudock.domain.hospital.HliSnapshot;
import com.example.ambudock.domain.hospital.HospitalStatus;
import com.example.ambudock.repository.HliSnapshotRepository;
import com.example.ambudock.repository.HospitalRepository;
import com.example.ambudock.repository.HospitalStatusRepository;
import com.example.ambudock.scheduler.client.NmcClient;
import com.example.ambudock.service.AppConfigService;
import com.example.ambudock.service.DisasterService;          // 재난 로직 통합 서비스
import com.example.ambudock.service.HliService;
import com.example.ambudock.service.HospitalOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Schedulers
 *
 * - A2: NMC(병원 실시간 상태) 수집 (주기: 60s, 초기 지연 15s 기본)
 *   -> 초기 버전 정책: **서울특별시만 수집** (25개 구 순회, 하드코딩)
 *   -> 병원 마스터(Hospital)와 상태(HospitalStatus)를 동시에 upsert
 *
 * - A3: HLI 계산 (주기: 60s, 초기 지연 30s 기본)
 *   -> HospitalStatus 전체를 순회하여 HLI 스냅샷 갱신
 *
 * - A4: MOIS 재난문자 수집/정규화/심각도 판정/TTL/지역 매핑/만료 (주기: 180s, 초기 지연 10s 기본)
 *   -> DisasterService에 완전 위임
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Schedulers {

    // ---------- 외부 API 클라이언트 ----------
    private final NmcClient nmcClient;

    // ---------- Repository ----------
    private final HospitalRepository hospitalRepo;
    private final HospitalStatusRepository statusRepo;
    private final HliSnapshotRepository hliRepo;

    // ---------- 서비스 ----------
    private final HliService hliService;
    private final AppConfigService cfg;          // HLI 기준/슬립 등은 그대로 AppConfig 사용
    private final DisasterService disasterService;
    private final HospitalOnboardingService onboardingService;

    // ---------- 수집 지역 (하드코딩: 서울특별시 25개 구) ----------
    // 초기 버전 부하 관리: 전국이 아닌 "서울특별시"만 순회
    private static final String SEOUL_SIDO = "서울특별시";
    private static final List<String> SEOUL_SIGUNGU = List.of(
            "강남구","강동구","강북구","강서구","관악구","광진구","구로구","금천구",
            "노원구","도봉구","동대문구","동작구","마포구","서대문구","서초구","성동구",
            "성북구","송파구","양천구","영등포구","용산구","은평구","종로구","중구","중랑구"
    );

    // 지역 간 호출 간격(하드코딩, ms)
    private static final int NMC_PER_REGION_SLEEP_MS = 300;

    // =====================================================================
    // A2: NMC 상태 수집 (서울특별시만, 구 단위 순회)
    // =====================================================================
    @Scheduled(
            fixedDelayString = "${scheduler.nmc_fetch.fixed_delay_ms}",
            initialDelayString = "${scheduler.nmc_fetch.initial_delay_ms}"
    )
    @Transactional
    public void a2_loadNmcStatus() {
        log.debug("A2: start NMC status load (Seoul-only)");

        // "서울특별시 × 25개 구" 조합 생성
        List<RegionPair> regions = resolveSeoulOnlyRegions();
        int regionCnt = regions.size();
        int processed = 0, skipped = 0;

        for (RegionPair rp : regions) {
            List<NmcStatusDto> list;
            try {
                // NMC OpenAPI 호출 — 지역(시도/시군구) 단위
                list = nmcClient.fetchLatestStatuses(rp.sido(), rp.sigungu());
            } catch (Exception e) {
                // 특정 구 호출 실패는 로그만 남기고 다음 구로 진행(격리)
                log.warn("A2: nmcClient.fetchLatestStatuses failed for {} {}: {}", rp.sido(), rp.sigungu(), e.toString());
                continue;
            }

            // 구별로 수집된 병원 상태를 순회하며 온보딩
            // 각 병원(HPID) 상태 upsert + 병원 마스터 온보딩/보정
            for (NmcStatusDto dto : list) {
                if (dto == null || dto.getHpid() == null) {
                    skipped++;
                    continue;
                }

                String hpid = dto.getHpid();

                // ① 병원/지역 온보딩 (없으면 생성, 있으면 메타 보정)
                //  - 최초 등장 HPID면 Kakao로 좌표/법정동 코드 확보 → Region/Hospital 생성
                //  - 이미 있더라도 주소/전화/좌표 보정 목적의 ensure 호출(멱등)
                if (!hospitalRepo.existsById(hpid)) {
                    boolean ok = onboardingService.ensureHospitalAndRegion(hpid, dto.getDutyname(), dto.getDutytel3());
                    if (!ok) {
                        // 온보딩 실패 시 상태 반영 불가 — 스킵
                        log.debug("A2: onboarding failed for hpid={}, skip status", hpid);
                        skipped++;
                        continue;
                    }
                } else {
                    // 이미 있더라도 주소/전화/좌표 보정 목적
                    onboardingService.ensureHospitalAndRegion(hpid, dto.getDutyname(), dto.getDutytel3());
                }

                // ② 최신 1건 조회 (동일 hvidate인지 비교해 update/insert 분기)
                Optional<HospitalStatus> latestOpt = statusRepo.findLatestByHpid(hpid, PageRequest.of(0, 1))
                        .stream().findFirst();

                Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

                // ③ 동일 hvidate(원천 갱신시각)면 update, 아니면 insert

                // ③ upsert 분기
                if (latestOpt.isPresent()
                        && dto.getHvidate() != null
                        && dto.getHvidate().equals(latestOpt.get().getHvidate())) {

                    // (a) 동일 hvidate → update: 같은 시점의 상태를 보정(예: 부분 필드 변경, 재수집)
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
                }
                // ③ upsert 분기
                else {
                    // (b) 신규 hvidate or 최초 → insert: 시점별 이력 보존
                    HospitalStatus s = new HospitalStatus();
                    s.setHospital(hospitalRepo.findById(hpid).orElse(null)); // 온보딩 직후이므로 정상적으로 존재해야 함
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

            // (선택) 지역 간 과도한 연속 호출을 조금 쉬게 하기 위한 슬립
            try {
                if (NMC_PER_REGION_SLEEP_MS > 0) {
                    Thread.sleep(NMC_PER_REGION_SLEEP_MS);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // 인터럽트 플래그 복구
            }
        }
        log.info("A2: finished NMC load (Seoul-only): regions={}, processed={}, skipped={}", regionCnt, processed, skipped);
    }

    // =====================================================================
    // A3: HLI 계산 (60s)
    // - NMC로 수집된 최신 HospitalStatus 레코드들을 대상으로
    // - HLI(Hospital Load Index) 점수/레벨을 계산해 스냅샷 테이블에 반영한다.
    //  - 신선도 임계값/패널티는 AppConfig에서 읽어와 운영 중에도 변경 가능.
    // =====================================================================
    @Scheduled(
            fixedDelayString = "${scheduler.hli_compute.fixed_delay_ms}",
            initialDelayString = "${scheduler.hli_compute.initial_delay_ms}"
    )
    @Transactional
    public void a3_computeHli() {
        log.debug("A3: start HLI compute");

        // --- 1) 운영 파라미터 로드 ---------------------------------------------
        // 신선도 임계(soft/hard)와 지연 패널티를 AppConfig 서비스에서 가져온다.
        // - soft: hvidate~지금까지 경과가 이 값(초) 이상이면 소프트 감점 적용
        // - hard: 이 값(초) 이상이면 데이터가 'stale' 판단(회색 처리/무시 등)
        // - penalty: soft구간에서 점수에 반영할 감점(또는 가중)
        int soft = cfg.getInt("STALE_SOFT_SEC", 300);
        int hard = cfg.getInt("STALE_HARD_SEC", 3600);
        int penalty = cfg.getInt("HLI_LAG_SOFT_PENALTY", 15);

        // --- 2) 대상 상태 조회 --------------------------------------------------
        // 모든 HospitalStatus를 불러와 순회한다.
        List<HospitalStatus> statuses = statusRepo.findAll();

        int updated = 0; // 이번 사이클에서 스냅샷을 갱신/작성한 건수 카운터

        for (HospitalStatus s : statuses) {
            try {
                // (a) 계산기 호출: ER base, ICU 보정, lagSec/신선도 반영 → score/level/staleLevel 등 산출
                var comp = hliService.compute(s, soft, hard, penalty);

                // (b) 스냅샷 upsert: statusId 1:1로 관리(동일 statusId면 갱신, 없으면 신규 생성)
                HliSnapshot snap = hliRepo.findByStatus_StatusId(s.getStatusId()).orElse(null);
                if (snap == null) {
                    snap = new HliSnapshot();
                    snap.setStatus(s);      // FK: 어떤 상태에서 계산된 스냅샷인지 추적
                    snap.setHospital(s.getHospital());
                }
                // (c) 계산 결과 매핑: 계산 시각/근거/세부 컴포넌트 저장
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

                // (d) 저장
                hliRepo.save(snap);
                updated++;
            } catch (Exception e) {
                // 한 레코드의 계산 실패가 전체 배치에 영향을 주지 않도록 방어
                log.warn("A3: compute failed for statusId={} cause={}", s.getStatusId(), e.toString());
            }
        }

        // --- 4) 리포팅 -----------------------------------------------------------
        // 처리한 상태 수와 실제 스냅샷 갱신 건수를 로그로 남겨 모니터링/알림 기준으로 활용
        log.info("A3: finished HLI compute, processedStatuses={}, snapshotsUpdated={}", statuses.size(), updated);
    }

    // =====================================================================
    // A4: MOIS 수집/정규화/판정/TTL/지역매핑/만료 (180s) → DisasterService 위임
    // =====================================================================
    @Scheduled(
            fixedDelayString = "${scheduler.mois_sync.fixed_delay_ms}",
            initialDelayString = "${scheduler.mois_sync.initial_delay_ms}"
    )
    @Transactional
    public void a4_loadMoisAndExpire() {
        log.debug("A4: start MOIS ingest+expire via DisasterService");
        var report = disasterService.ingestLatestFromMois();  // 한 줄 위임
        log.info("A4: MOIS ingest result {}", report);
    }

    //반환된 리스트를 A2 스케줄러가 순회하면서 NMC API를 구 단위로 호출
    private List<RegionPair> resolveSeoulOnlyRegions() {
        List<RegionPair> out = new ArrayList<>(SEOUL_SIGUNGU.size());
        for (String gu : SEOUL_SIGUNGU) out.add(new RegionPair(SEOUL_SIDO, gu));
        return out;
    }

    // 간단 지역쌍 레코드
    private record RegionPair(String sido, String sigungu) {}
}
