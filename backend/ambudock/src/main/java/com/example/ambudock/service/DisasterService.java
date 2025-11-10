package com.example.ambudock.service;

import com.example.ambudock.domain.common.enums.MatchLevel;
import com.example.ambudock.domain.common.enums.Severity;
import com.example.ambudock.domain.disaster.AlertKeyword;
import com.example.ambudock.domain.disaster.AlertRegionLink;
import com.example.ambudock.domain.disaster.DisasterAlert;
import com.example.ambudock.domain.hospital.Hospital;
import com.example.ambudock.domain.region.Region;
import com.example.ambudock.dto.MoisAlertDto;
import com.example.ambudock.dto.disaster.DisasterDtos;
import com.example.ambudock.repository.AlertRegionLinkRepository;
import com.example.ambudock.repository.DisasterAlertRepository;
import com.example.ambudock.repository.HospitalRepository;
import com.example.ambudock.repository.RegionRepository;
import com.example.ambudock.scheduler.client.MoisClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisasterService {

    // ===== 외부 의존 (MOIS 호출) =====
    private final MoisClient moisClient;

    private final EntityManager em;
    private final RegionRepository regionRepo;
    private final DisasterAlertRepository disasterRepo;
    private final AlertRegionLinkRepository linkRepo;
    private final HospitalRepository hospitalRepo;

    // ===== AppConfig (운영 파라미터) =====
    private final AppConfigService cfg;

    // ===== 지역 파싱 보조 상수/유틸 (다지역 밀집 표기 대응) =====
    // 시/도 정식 명칭 세트(17개)
    private static final Set<String> SIDO_SET = Set.of(
            "서울특별시","부산광역시","대구광역시","인천광역시","광주광역시","대전광역시","울산광역시",
            "세종특별자치시",
            "경기도","강원특별자치도",
            "충청북도","충청남도",
            "전북특별자치도","전라남도",
            "경상북도","경상남도",
            "제주특별자치도"
    );
    // 시/군/구로 볼 수 있는 접미사 정규식
    private static final String SGG_SUFFIX_RE = ".*(시|군|구)$";

    /* =========================================================
     * 1) 수집/정규화/판정/TTL/링킹 (Ingest 파이프라인)
     * ========================================================= */

    /**
     * 오늘(Asia/Seoul 기준) 생성된 최신 MOIS 알림을 불러와
     * - 정규화/심각도 판정/TTL 설정/지역 매핑(AlertRegionLink 생성)까지 처리하고,
     * - 만료(expirePastAlerts)도 수행한다.
     *
     * @return IngestReport: 삽입/갱신/링크 생성/만료 개수 요약
     */
    @Transactional
    public IngestReport ingestLatestFromMois() {
        // MoisClient는 '정규화된 원문 DTO'만 반환 (md101Sn, issuedAt, locationName, msg ...)
        List<MoisAlertDto> raw = moisClient.fetchLatestAlerts();

        // DTO ingest (upsert + 룰 기반 severity + TTL + 지역 링크)
        IngestReport r = ingestDtos(raw);

        // TTL 기반 만료(isActive=false) 처리
        int expired = expirePastAlerts();
        return r.withExpired(expired);
    }

    /**
     * 외부에서 주입된 MOIS 정규화 DTO들을 한 번에 ingest(수집·정규화·판정·링킹)한다.
     */
    @Transactional
    public IngestReport ingestDtos(List<MoisAlertDto> dtos) {
        // 0) 방어 로직: 입력이 없으면 바로 빈 리포트 반환
        if (dtos == null || dtos.isEmpty()) return new IngestReport(0, 0, 0, 0);

        // 1) 키워드 룰 로드 및 정규식 컴파일
        RuleSet rules = loadRules();

        // 2) TTL 파라미터 로드 (시간 단위)
        int ttlBlockH  = cfg.getInt("ALERT_TTL_BLOCK_H", 4);
        int ttlWarnH   = cfg.getInt("ALERT_TTL_WARN_H", 2);
        int ttlIgnoreH = cfg.getInt("ALERT_TTL_IGNORE_H", 1);

        // 실적 집계(삽입/갱신/링킹 건수)
        int ins = 0, upd = 0, links = 0;

        // 3) 개별 알림 ingest 루프
        for (MoisAlertDto dto : dtos) {
            if (dto == null) continue; // 방어

            // 3.1) 고유 키(md101Sn)는 반드시 필요 — 없으면 해당 건은 무시
            String key = nz(dto.getMd101Sn());
            if (key == null) {
                log.debug("[DisasterIngest] skip: missing md101Sn");
                continue;
            }

            // 3.2) 필드 정규화/길이 보정
            String msg     = clip(nz(dto.getMsg()),           4000, "(빈 메시지)");
            String locName = clip(nz(dto.getLocationName()),  120,  "미상");
            String locId   = clip(nz(dto.getLocationId()),    40,   "UNKNOWN"); // 신규 스펙에서 없을 수 있음

            //      - 발행시각(issuedAt) 누락 시에는 현재 시각으로 대체(밀리초 정밀도 유지)
            Instant issued = (dto.getIssuedAt() == null)
                    ? Instant.now().truncatedTo(ChronoUnit.MILLIS)
                    : dto.getIssuedAt();

            // 3.3) 심각도 판정
            Severity sev = decideSeverity(
                    rules,
                    msg,
                    StringUtils.defaultString(dto.getEmrgStepName(), ""),
                    StringUtils.defaultString(dto.getHazardName(), "")
            );

            // 3.4) TTL 계산 (issuedAt + TTL)
            Instant expiresAt = issued.plus(
                    switch (sev == null ? Severity.IGNORE : sev) {
                        case BLOCK -> ttlBlockH;
                        case WARN  -> ttlWarnH;
                        default    -> ttlIgnoreH;
                    },
                    ChronoUnit.HOURS
            );

            // 3.5) Upsert by md101Sn
            //      - 같은 알림(md101Sn)이 재수신되면 기존 레코드를 '보강/연장'한다.
            //      - issuedAt/location*/msg/severity/expiresAt 을 상황에 따라 갱신한다.
            DisasterAlert alert = findByMd101Sn(key);

            if (alert == null) {
                // INSERT 경로: 최초 수신
                alert = new DisasterAlert();

                // === 식별 및 기본 메타 ===
                alert.setMd101Sn(key);  // 고유 식별자(동일 알림 재수신의 기준)
                alert.setIssuedAt(issued); // 발령 시각(UTC Instant), null 방지 위해 상단에서 보정됨
                alert.setLocationId(locId); // 수신지역 코드(없을 수 있음 → "UNKNOWN" 보정)
                alert.setLocationName(locName); // 수신지역 명(예: "서울특별시 강남구")
                alert.setMsg(msg); // 원문 메시지(수정 공지 시 추후 업데이트 경로에서 갱신)
                alert.setSeverity(sev == null ? Severity.IGNORE : sev); // DB NOT NULL 보장
                alert.setExpiresAt(expiresAt); // TTL 적용
                alert.setIsActive(true); // 신규 삽입 시 활성 상태
                em.persist(alert); // ← 새 DisasterAlert 저장
                ins++;
            } else {
                // UPDATE 경로: 동일 md101Sn 재수신 (정정/연장/추가 지역 케이스 포함)
                // - 기존에 null이던 필드는 새 값으로 채움
                // - 메시지는 항상 최신값으로 덮어씀(변경/수정 공지 반영)
                // - expiresAt은 더 길다면 연장(짧아지게 줄이지 않음 — 안전측)
                if (alert.getIssuedAt() == null)                alert.setIssuedAt(issued);
                if (alert.getLocationId() == null)              alert.setLocationId(locId);
                if (alert.getLocationName() == null)            alert.setLocationName(locName);

                // 원문 메시지는 '항상 최신'으로 덮어써서 공지 정정을 반영
                if (msg != null)                                alert.setMsg(msg);
                // 판정 심각도는 새 결론이 있으면 치환(운영정책: 최신 판정 우선)
                if (sev != null)                                alert.setSeverity(sev);
                // 만료시각은 더 길어질 때만 연장(짧아지게 줄이지 않음 — 안전측)
                if (alert.getExpiresAt() == null || alert.getExpiresAt().isBefore(expiresAt)) {
                    alert.setExpiresAt(expiresAt);
                }
                alert.setIsActive(true); // 재수신 시 활성화 보장
                em.merge(alert);  // ← 기존 DisasterAlert 갱신
                upd++;
            }

            // 3.6) 지역 매핑 재구성
            //      - 한 알림에 여러 지역이 딸려오는 케이스(쉼표/구분자 포함) 대응
            //      - 기존 링크는 전부 삭제 후 재생성(UNIQUE(alert_id, region_id)로 중복 방지)
            //      - 매핑 정밀도: SIDO / SIGUNGU / DONG (현재 구현에선 SIGUNGU > DONG > SIDO 또는 정책에 맞게)
            links += relinkRegions(alert, locName);
        }

        // 4) 만료 처리는 호출자 정책에 따라 별도로 수행
        //    - ingestLatestFromMois()는 ingest 후 expirePastAlerts()까지 호출
        //    - 이 메서드는 ingest만 담당하므로 expired=0을 담아 리포트 반환
        return new IngestReport(ins, upd, links, 0);
    }


    /**
     * TTL 만료 처리.
     *  - 활성(isActive=true) 알림 중 expiresAt < now 인 것은 isActive=false 로 전환한다.
     *  - DisasterAlertRepository.findActiveAlerts() 는 IGNORE 제외, isActive=true 조건으로 최신순을 반환.
     */
    @Transactional
    public int expirePastAlerts() {
        List<DisasterAlert> active = disasterRepo.findActiveAlerts();
        if (active.isEmpty()) return 0;

        int expired = 0;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for (DisasterAlert a : active) {
            if (a.getExpiresAt() != null && a.getExpiresAt().isBefore(now)) {
                a.setIsActive(false);
                em.merge(a);
                expired++;
            }
        }
        return expired;
    }

    /* =========================================================
     * 2) 평가 API (후보 제외/배지 생성)
     *    - 병원 HPID 목록에 대해 BLOCK 제외/WARN 배지를 계산
     * ========================================================= */

    /**
     * 병원 HPID 리스트에 대해 재난문자 2차 필터 평가를 수행.
     *  - 같은 Region 내 '활성 경보 링크'들 중에서
     *    severity(BLOCK>WARN>IGNORE) → issuedAt(desc) → matchLevel 정밀도 순으로 정렬하여 최상위 1건 선택.
     *  - BLOCK && ALERT_BLOCK_EXCLUDE=1 → exclude
     *  - WARN → "이동차질 가능(행정명)" 배지
     */
    @Transactional(readOnly = true)
    public DisasterDtos.EvaluateResponse evaluate(List<String> hpids) {
        // 운영 파라미터: BLOCK 등급이면 후보를 제외할지 여부 (기본값 "1"=제외)
        // AppConfig("ALERT_BLOCK_EXCLUDE")가 "1"이면 BLOCK 시 exclude=true 처리
        boolean blockExclude = "1".equals(cfg.get("ALERT_BLOCK_EXCLUDE", "1"));

        // 각 HPID별 평가 결과를 담을 리스트
        List<DisasterDtos.EvaluateItem> results = new ArrayList<>();

        // === HPID 단위로 반복 평가 ===
        for (String hpid : hpids) {
            // 병원 마스터 조회 (존재하지 않거나 Region이 없으면 평가 불가 → 영향 없음 처리)
            Hospital h = hospitalRepo.findById(hpid).orElse(null);
            if (h == null || h.getRegion() == null) {
                // excluded=false, badges 빈 리스트, severity=null 로 결과 생성
                results.add(new DisasterDtos.EvaluateItem(hpid, false, List.of(), null));
                continue;
            }

            // 병원이 속한 Region에 연결된 '활성(Alert.isActive=true) 재난문자 링크' 조회
            List<AlertRegionLink> links = linkRepo.findActiveByRegion(h.getRegion().getRegionId());
            if (links.isEmpty()) {
                // 활성 경보가 지역에 하나도 없으면 영향 없음
                results.add(new DisasterDtos.EvaluateItem(hpid, false, List.of(), null));
                continue;
            }

            // 동일 Region의 여러 활성 경보 후보들 중 "최상위 1건"을 고르기 위한 정렬 규칙:
            //  1) severity: BLOCK(3) > WARN(2) > IGNORE(1)   ← sevRank() 가 숫자로 환산
            //  2) issuedAt: 최신 우선(내림차순)               ← Comparator.reverseOrder()
            //  3) matchLevel: SIGUNGU(3) > DONG(2) > SIDO(1) ← matchRank()
            //
            // 주의: 첫 번째 키(severity)는 "내림차순"이므로 .reversed()를 사용.
            links.sort(
                    Comparator
                            .comparingInt((AlertRegionLink l) -> sevRank(l.getAlert().getSeverity())) // 1차: severity 등급
                            .reversed()                                                                // BLOCK 우선이므로 내림차순
                            .thenComparing(                                                            // 2차: 발령시각 최신 우선
                                    (AlertRegionLink l) -> l.getAlert().getIssuedAt(),
                                    Comparator.reverseOrder()
                            )
                            .thenComparingInt(l -> matchRank(l.getMatchLevel()))                       // 3차: 매칭 정밀도
            );

            // 정렬 결과의 최상위 1건(해당 병원의 지역에 대해 "현재 가장 강한/가까운" 경보)
            AlertRegionLink best = links.get(0);
            Severity sev = best.getAlert().getSeverity(); // BLOCK / WARN / IGNORE (null 가능성 방어는 아래에서)

            // BLOCK 제외 정책 적용:
            //  - sev가 BLOCK이고, blockExclude=true 라면 이 병원은 "후보 제외(excluded=true)"
            boolean excluded = (sev == Severity.BLOCK) && blockExclude;

            // WARN 배지 생성:
            //  - sev가 WARN인 경우, 기획서 규칙에 따라 "이동차질 가능(행정명)" 한 줄 배지 부여
            //  - 행정명은 best.getRegion().getNameFull() (예: "서울특별시 강남구")
            List<String> badges = (sev == Severity.WARN)
                    ? List.of("이동차질 가능(" + best.getRegion().getNameFull() + ")")
                    : List.of();

            // HPID별 평가 결과 항목 구성:
            //  - excluded: BLOCK 정책에 의해 제외 여부
            //  - badges  : WARN일 때의 경고 배지(없으면 빈 리스트)
            //  - severity: 문자열로 전달(null 안전)
            results.add(new DisasterDtos.EvaluateItem(
                    hpid,
                    excluded,
                    badges,
                    (sev == null ? null : sev.name())
            ));
        }

        // 최종 응답 DTO로 포장
        return new DisasterDtos.EvaluateResponse(results);
    }


    // severity 정렬 우선순위: BLOCK(3) > WARN(2) > IGNORE(1)
    private int sevRank(Severity s) {
        if (s == null) return 0;
        return switch (s) {
            case BLOCK -> 3;
            case WARN  -> 2;
            case IGNORE -> 1;
        };
    }

    // matchLevel 정밀도 순위 (기획서/메모 기준): SIGUNGU > DONG > SIDO
    private int matchRank(MatchLevel m) {
        if (m == null) return 0;
        return switch (m) {
            case SIGUNGU -> 3;
            case DONG    -> 2;
            case SIDO    -> 1;
        };
    }

    /* =========================================================
     * 내부 유틸: 룰 로딩/심각도 판정/지역 후보/조인/헬퍼
     * ========================================================= */

    /**
     * AlertKeyword 테이블에서 enabled=true 규칙을 priority ASC로 로드해
     * 정규식을 미리 컴파일한 RuleSet을 만든다.
     */
    private RuleSet loadRules() {
        // 1) DB에서 활성 규칙을 우선순위(오름차순)로 로드
        //    - enabled=true 인 것만
        //    - priority ASC: 숫자가 작을수록 먼저 평가
        List<AlertKeyword> rules = em.createQuery(
                "select k from AlertKeyword k where k.enabled = true order by k.priority asc",
                AlertKeyword.class
        ).getResultList();

        // 2) 정규식 사전 컴파일
        //    - 실패(문법 오류)한 패턴은 건너뜀
        //    - 필요시 CASE_INSENSITIVE/UNICODE_CASE 등의 플래그를 적용할 수 있음
        List<CompiledRule> compiled = new ArrayList<>(rules.size());
        for (AlertKeyword k : rules) {
            try {
                // (a) 널/공백 패턴은 무시
                String pattern = k.getPattern();
                if (pattern == null || pattern.isBlank()) {
                    log.warn("[Rule] empty pattern skipped: id={}", k.getRuleId());
                    continue;
                }

                // (b) 정규식 컴파일
                //     기본은 플래그 없이 컴파일.
                Pattern p = Pattern.compile(pattern);

                compiled.add(new CompiledRule(k.getSeverity(), p));
            } catch (java.util.regex.PatternSyntaxException e) {
                // 정규식 문법 오류: 해당 규칙만 건너뛰고 계속 진행
                log.warn("[Rule] invalid regex skipped: id={} pattern={} cause={}",
                        k.getRuleId(), k.getPattern(), e.toString());
            } catch (Exception e) {
                // 예외 상황(예: OOM 등) 방어 — 서비스 지속성 확보
                log.warn("[Rule] regex compile failed: id={} pattern={} cause={}",
                        k.getRuleId(), k.getPattern(), e.toString());
            }
        }

        // 3) RuleSet으로 감싸 반환
        //    - 필요하면 Collections.unmodifiableList(...)로 불변화도 가능
        return new RuleSet(compiled);
    }


    /**
     * 심각도 판정 (페이지2 정책)
     *  1) 정규식 룰 우선: enabled=true, priority ASC 순으로 매칭.
     *     - IGNORE가 하나라도 매칭되면 즉시 IGNORE 반환(해제/종료는 무조건 무력화).
     *     - BLOCK/WARN은 누적 후 BLOCK 우선.
     *  2) 보조 힌트: 단계명/재난유형/본문 텍스트를 소문자 결합하여
     *     해제/대피/통제/주의/기상 키워드로 보정(룰에 안 걸린 잔여 케이스 보완).
     *  3) 아무 단서가 없으면 보수적으로 IGNORE.
     */
    private Severity decideSeverity(RuleSet rules, String msg, String emrgStepName, String hazardName) {
        // 본문이 null일 수 있으므로 빈 문자열로 정규화
        String text = StringUtils.defaultString(msg, "");

        // -----------------------------
        // 1) 정규식 룰 적용 (우선순위: IGNORE > BLOCK > WARN)
        // -----------------------------
        boolean seenBlock = false, seenWarn = false; // BLOCK/WARN 발견 여부 플래그
        for (CompiledRule r : rules.compiled()) {    // priority ASC로 정렬된 룰 순회
            // 현재 룰의 정규식이 본문과 매칭되는지 검사
            if (!r.pattern().matcher(text).find()) continue;

            // IGNORE 규칙이 하나라도 매칭되면 즉시 반환 (해제/종료 등은 최우선 무효화)
            if (r.severity() == Severity.IGNORE) return Severity.IGNORE;

            // BLOCK/WARN은 누적 기록만 하고 루프 계속(다른 룰과의 충돌을 고려)
            if (r.severity() == Severity.BLOCK)  seenBlock = true;
            if (r.severity() == Severity.WARN)   seenWarn  = true;
        }
        // 룰 레벨에서 결론: BLOCK이 한 번이라도 걸리면 BLOCK, 아니면 WARN이 있으면 WARN
        if (seenBlock) return Severity.BLOCK;
        if (seenWarn)  return Severity.WARN;

        // -----------------------------
        // 2) 보조 힌트 기반 판정
        //    - 룰로 결론이 안 났을 때 단계명/재난유형/본문을 합쳐 소문자 비교
        // -----------------------------
        String all = (StringUtils.defaultString(emrgStepName, "") + " "
                + StringUtils.defaultString(hazardName, "") + " " + text).toLowerCase();

        // '해제/종료/정상화'가 포함되면 무조건 IGNORE (최우선 무효화)
        if (all.contains("해제") || all.contains("종료") || all.contains("정상화"))
            return Severity.IGNORE;

        // 대피/전면 통제/출입 통제 등 즉각적 위험 신호는 BLOCK
        if (all.contains("대피") || all.contains("전면 통제") || all.contains("전면통제") || all.contains("출입 통제"))
            return Severity.BLOCK;

        // 주의/주의보/기상 경보(호우/강풍/폭염/폭설/태풍 등)는 WARN
        if (all.contains("주의") || all.contains("주의보") || all.contains("호우") || all.contains("강풍")
                || all.contains("폭염") || all.contains("폭설") || all.contains("태풍"))
            return Severity.WARN;

        // -----------------------------
        // 3) 명시적 단서가 없으면 보수적으로 IGNORE
        // -----------------------------
        return Severity.IGNORE;
    }


    /**
     * 동일 알림(alert)에 연결된 기존 지역 링크(AlertRegionLink)를 모두 삭제한 뒤,
     * locationName(예: "서울특별시 강남구, 송파구")을 파싱해 얻은 Region 후보들과
     * 다시 N:M 링크를 구성한다.
     */
    private int relinkRegions(DisasterAlert alert, String locationName) {
        // 기존 링크 전부 삭제
        em.createQuery("delete from AlertRegionLink l where l.alert = :a")
                .setParameter("a", alert)
                .executeUpdate();

        // locationName → Region 후보 목록 생성
        //- "서울특별시 강남구/송파구", "서울 강남구, 성동구" 등 다양한 표기를 파싱
        // - resolveRegionCandidates는 내부적으로 중복 Region을 제거(Set)하고
        //  SIDO/SIGUNGU/DONG 매칭 레벨을 부여함.
        int created = 0;
        for (RegionCandidate c : resolveRegionCandidates(locationName)) {
            try {
                AlertRegionLink link = new AlertRegionLink();
                link.setAlert(alert);
                link.setRegion(c.region());
                link.setMatchLevel(c.level());
                em.persist(link);
                created++;
            } catch (Exception ignore) {
                // UNIQUE(alert_id, region_id) 충돌 시 무시 (동일 지역 중복 링크 방지)
            }
        }
        return created;
    }

    /**
     * 수신지역명(locationName)을 느슨하게 파싱하여 Region 후보 목록 생성.
     * - 구분자: ',', '·', '/', '및', '그리고' 등을 쉼표로 통일 후 분할.
     * - 토큰: "시/도", "시/군/구", "읍/면/동" 순으로 최대 3토큰.
     * - 매칭 순서:
     *    SIDO+SIGUNGU+DONG → DONG 정밀 링크
     *    SIDO+SIGUNGU      → SIGUNGU 링크
     *    SIDO              → SIDO 링크
     * - Region 테이블에 실제로 존재하는 것만 채택.
     */
    private List<RegionCandidate> resolveRegionCandidates(String locationName) {
        if (StringUtils.isBlank(locationName)) return List.of();

        // 표기 편차 줄이기: 구분자 통일 후 split
        String[] parts = locationName
                .replace('ㆍ', '·').replace('／', '/')
                .replace("및", ",").replace("그리고", ",")
                .split("[,·/]");

        Set<Long> seen = new HashSet<>();
        List<RegionCandidate> out = new ArrayList<>();

        //클린업: 앞뒤 공백 제거 + 다중 공백을 단일 공백으로
        for (String raw : parts) {
            String s = raw.trim().replaceAll("\\s+", " ");
            if (s.isBlank()) continue;

            // 토큰화: 최대 3토큰(시도/시군구/동) 가정
            String[] tok = s.split(" ");
            String sido    = tok.length >= 1 ? tok[0] : null;
            String sigungu = tok.length >= 2 ? tok[1] : null;
            String dong    = tok.length >= 3 ? tok[2] : null;

            // ── 4) 매칭 시도 (존재하는 Region만 채택)

            //4-1) SIDO+SIGUNGU+DONG → DONG 정밀
            if (sido != null && sigungu != null && dong != null) {
                Region r = findRegionByNames(sido, sigungu, dong);
                if (r != null && seen.add(r.getRegionId())) {
                    out.add(new RegionCandidate(r, MatchLevel.DONG));
                    continue;
                }
            }
            // 4-2) SIDO+SIGUNGU → SIGUNGU
            if (sido != null && sigungu != null) {
                Region r = findRegionByNames(sido, sigungu, null);
                if (r != null && seen.add(r.getRegionId())) {
                    out.add(new RegionCandidate(r, MatchLevel.SIGUNGU));
                    // SIGUNGU가 성공하면 SIDO까지 내려가지 않아도 실용상 충분
                    continue;
                }
            }
            // SIDO → SIDO
            if (sido != null) {
                Region r = findRegionByNames(sido, null, null);
                if (r != null && seen.add(r.getRegionId())) {
                    out.add(new RegionCandidate(r, MatchLevel.SIDO));
                }
            }
        }

        // === 폴백: 구분자 없이 시/도-시/군/구 쌍이 연속으로 붙은 "밀집 형태" 처리 ===
        // - out이 비었거나, parts가 1조각 뿐인데 시/도 명칭이 2회 이상 등장하면 밀집 형태로 간주
        if (out.isEmpty() || (parts.length == 1 && looksDenseMultiSido(locationName))) {
            String dense = locationName.trim().replaceAll("\\s+", " ");
            for (String[] triple : splitDenseRegionPairs(dense)) {
                String sido = triple[0];
                String sigungu = triple[1];
                String dong = triple[2];

                // 세종특별자치시 등 SGG가 없으면 SIDO 레벨만 시도
                Region r = (sido != null && sigungu != null)
                        ? findRegionByNames(sido, sigungu, dong)
                        : findRegionByNames(sido, null, null);

                if (r == null) continue;

                MatchLevel level = (sido != null && sigungu != null)
                        ? (dong != null ? MatchLevel.DONG : MatchLevel.SIGUNGU)
                        : MatchLevel.SIDO;

                if (seen.add(r.getRegionId())) {
                    out.add(new RegionCandidate(r, level));
                }
            }
        }

        return out;
    }

    /**
     * Region 테이블에서 (시도/시군구/동) 조합으로 1건을 구한다.
     * - DONG 완전일치 → SIGUNGU 일치 → SIDO 일치 우선
     */
    private Region findRegionByNames(String sido, String sigungu, String dong) {

        // ── 1) 시도 + 시군구 + 동 3토큰 완전일치 (가장 정밀)
        if (sido != null && sigungu != null && dong != null) {
            TypedQuery<Region> q = em.createQuery(
                    "select r from Region r where r.nameSido=:s and r.nameSigungu=:g and r.nameDong=:d",
                    Region.class);
            q.setParameter("s", sido);
            q.setParameter("g", sigungu);
            q.setParameter("d", dong);
            List<Region> res = q.setMaxResults(1).getResultList();
            if (!res.isEmpty()) return res.get(0);
        }

        // ── 2) 시도 + 시군구 2토큰 완전일치 (구 레벨)
        if (sido != null && sigungu != null) {
            TypedQuery<Region> q = em.createQuery(
                    "select r from Region r where r.nameSido=:s and r.nameSigungu=:g order by r.regionId asc",
                    Region.class);
            q.setParameter("s", sido);
            q.setParameter("g", sigungu);
            List<Region> res = q.setMaxResults(1).getResultList();
            if (!res.isEmpty()) return res.get(0);
        }

        // ── 3) 시도 1토큰 완전일치 (시/도 레벨)
        if (sido != null) {
            TypedQuery<Region> q = em.createQuery(
                    "select r from Region r where r.nameSido=:s order by r.regionId asc",
                    Region.class);
            q.setParameter("s", sido);
            List<Region> res = q.setMaxResults(1).getResultList();
            if (!res.isEmpty()) return res.get(0);
        }

        // 어떤 조합으로도 찾지 못한 경우
        return null;
    }

    /**
     * md101Sn(고유번호)로 DisasterAlert 1건 조회 (없으면 null)
     */
    private DisasterAlert findByMd101Sn(String sn) {
        List<DisasterAlert> found = em.createQuery(
                        "select a from DisasterAlert a where a.md101Sn = :sn",
                        DisasterAlert.class
                ).setParameter("sn", sn)
                .setMaxResults(1)
                .getResultList();
        return found.isEmpty() ? null : found.get(0);
    }

    // ===== 문자열 헬퍼 =====
    private static String nz(String s) { return (s == null || s.isBlank()) ? null : s; }
    private static String clip(String s, int maxLen, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    // ===== 다지역 밀집 표기 감지/분해 유틸 =====

    /** locationName 안에 시/도 명칭이 2회 이상 등장하면 '밀집 형태'로 간주 */
    private static boolean looksDenseMultiSido(String s) {
        if (s == null) return false;
        int count = 0;
        for (String sido : SIDO_SET) {
            int idx = -1;
            while ((idx = s.indexOf(sido, idx + 1)) != -1) {
                count++;
                if (count >= 2) return true;
            }
        }
        return false;
    }

    /**
     * 구분자 없이 "[시/도] [시/군/구] [시/도] [시/군/구] ..." 식으로 이어진 문자열을
     * [sido, sigungu, (opt)dong] 쌍 목록으로 뽑아낸다.
     * - 로직: 토큰을 순회하며 SIDO가 보이면 '새 구간' 시작, 이어지는 첫 SGG 접미사 토큰을 붙인다.
     * - 세종특별자치시처럼 SGG가 없는 시/도는 SIDO만 후보로 만든다.
     */
    private static List<String[]> splitDenseRegionPairs(String s) {
        List<String[]> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;

        String[] toks = s.trim().replaceAll("\\s+", " ").split(" ");
        String currentSido = null;
        String pendingSgg  = null;
        String pendingDong = null;

        for (String t : toks) {
            if (SIDO_SET.contains(t)) {
                // 직전 구간 flush
                if (currentSido != null) {
                    out.add(new String[]{ currentSido, pendingSgg, pendingDong });
                }
                // 새 SIDO 시작
                currentSido = t;
                pendingSgg  = null;
                pendingDong = null;
            } else if (t.matches(SGG_SUFFIX_RE)) {
                pendingSgg = t;
            } else {
                // (선택) 읍/면/동 지원 시: if (t.matches(".*(동|읍|면)$")) pendingDong = t;
            }
        }
        // 마지막 구간 flush
        if (currentSido != null) {
            out.add(new String[]{ currentSido, pendingSgg, pendingDong });
        }
        return out;
    }

    // ===== 결과 요약 DTO =====
    @Getter
    public static class IngestReport {
        private final int inserted;     // 새로 삽입된 DisasterAlert 수
        private final int updated;      // 갱신된 DisasterAlert 수
        private final int linksCreated; // 생성된 AlertRegionLink 수
        private final int expired;      // 이번 사이클에서 만료 처리된 Alert 수

        public IngestReport(int inserted, int updated, int linksCreated, int expired) {
            this.inserted = inserted;
            this.updated = updated;
            this.linksCreated = linksCreated;
            this.expired = expired;
        }
        public IngestReport withExpired(int exp){ return new IngestReport(inserted, updated, linksCreated, exp); }

        @Override public String toString() {
            return "IngestReport{ins=%d,upd=%d,links=%d,exp=%d}".formatted(inserted, updated, linksCreated, expired);
        }
    }

    // ===== 내부 전용 레코드 =====
    private record RegionCandidate(Region region, MatchLevel level) {}
    private record CompiledRule(Severity severity, Pattern pattern) {}
    private record RuleSet(List<CompiledRule> compiled) {}
}
