package com.example.ambudock.service;

import com.example.ambudock.domain.common.enums.HliLevel;
import com.example.ambudock.domain.common.enums.IcuBucket;
import com.example.ambudock.domain.common.enums.StaleLevel;
import com.example.ambudock.domain.hospital.HospitalStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HliService {

    /**
     * erBaseScore
     *
     * - 응급실(ER) 가용 hvec 값에 따라 기본 점수(정수)를 반환한다.
     *
     */
    private short erBaseScore(int hvec) {
        if (hvec <= 0) return 3;
        if (hvec == 1) return 25;
        if (hvec == 2) return 40;
        if (hvec == 3) return 52;
        if (hvec == 4) return 62;
        if (hvec == 5) return 70;
        if (hvec <= 7) return 76;
        if (hvec <= 10) return 82;
        if (hvec <= 15) return 87;
        if (hvec <= 20) return 91;
        return 94;
    }

    /**
     * bucket
     *
     * - ICU(중환자실) 가용 수의 총합(avail)에 따라 IcuBucket enum을 반환.
     * - IcuBucket은 NONE, CRITICAL_LOW, LOW, MID, HIGH 등으로 구분되어
     *   compute에서 점수 조정(adjust)을 결정한다.
     */
    private IcuBucket bucket(int avail) {
        if (avail <= 0) return IcuBucket.NONE;
        if (avail == 1) return IcuBucket.CRITICAL_LOW;
        if (avail <= 3) return IcuBucket.LOW;
        if (avail <= 9) return IcuBucket.MID;
        return IcuBucket.HIGH;
    }

    /**
     * adjust
     *
     * - ICU 버킷에 따른 점수 보정값(정수)을 반환.
     * - 음수면 점수 감소, 양수면 점수 증가.
     */
    private short adjust(IcuBucket b) {
        return switch (b) {
            case NONE -> -20;
            case CRITICAL_LOW -> -10;
            case LOW -> -5;
            case MID -> 0;
            case HIGH -> 6;
        };
    }

    private static final ObjectMapper om = new ObjectMapper();


    /**
     * compute
     *
     * 입력: HospitalStatus s (NMC로부터 수신된 실시간 상태)
     * 파라미터:
     *  - soft: soft staleness threshold (초) -> 이 내이면 FRESH
     *  - hard: hard staleness threshold (초) -> 이 이상이면 HARD (데이터 신뢰 없음)
     *  - lagPenalty: soft 구간에서 적용할 점수 패널티(정수)
     *
     * 반환: Computed (계산된 HLI 관련 메타/점수/이유/계산시각)
     *
     */
    public Computed compute(HospitalStatus s, int soft, int hard, int lagPenalty) {

        // ICU 관련 필드들을 합산 (null 안전)
        int icu = nz(s.getHv2()) + nz(s.getHv3()) + nz(s.getHv4())
                + nz(s.getHv5()) + nz(s.getHv6()) + nz(s.getHvoc());

        // ICU 총합에 따라 버킷 결정
        IcuBucket buck = bucket(icu);

        // ER base score (hvec -> base)
        short base = erBaseScore(s.getHvec());

        // ICU 버킷에 따른 보정값
        short adj = adjust(buck);

        // hvidate는 데이터 소스(NMC)가 제공하는 갱신시각(Instant)으로 가정.
        // null이면 Instant.EPOCH로 대체
        Instant hviInstant = (s.getHvidate() == null) ? Instant.EPOCH : s.getHvidate();

        // 현재 시각(UTC 기준), 밀리초 단위 절단(truncate)으로 일관성 유지
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // 갱신 지연(초) 계산 — now가 hvi보다 이전이면 음수(미래 시점 데이터)
        int lagSec = (int) Duration.between(hviInstant, now).getSeconds();
        StaleLevel stale = lagSec < soft ? StaleLevel.FRESH :
                (lagSec < hard ? StaleLevel.SOFT : StaleLevel.HARD);

        short score;
        HliLevel level;

        // staleness 결정 (soft/hard 임계값 비교)
        if (stale == StaleLevel.HARD) {
            // HARD: 데이터 신뢰 불가 → 회색(무시) 등으로 처리 (score 0)
            score = 0;
            level = HliLevel.GRAY;
        } else {

            // 기본 점수 = ER base + ICU adjust
            int tmp = base + adj;

            // soft 영역이면 lagPenalty 적용
            if (stale == StaleLevel.SOFT) tmp -= lagPenalty;

            // 점수 범위를 0..100로 강제
            tmp = Math.min(100, Math.max(0, tmp));
            score = (short) tmp;

            // 최종 HLI 레벨 결정 규칙:
            // - ER 가 0이면 (응급실 당장 사용 불가) -> RED (우선)
            // - score 기준으로 GREEN/YELLOW/RED 매핑
            if (s.getHvec() == 0) level = HliLevel.RED;
            else if (score >= 70) level = HliLevel.GREEN;
            else if (score >= 40) level = HliLevel.YELLOW;
            else level = HliLevel.RED;
        }

        // 계산 근거를 사람이 읽을 수 있는 형태로 수집 (로깅/감사 목적)
        List<String> reason = List.of(
                "ER=" + s.getHvec() + " → base=" + base,
                "ICU=" + icu + " → " + buck + " → adj=" + adj,
                "lag=" + lagSec + "s → " + stale +
                        (stale == StaleLevel.SOFT ? " → -" + lagPenalty : "")
        );

        // reason을 JSON 문자열로 직렬화 — DB 저장용/응답용으로 사용 가능
        String reasonJson;
        try {
            reasonJson = om.writeValueAsString(reason);
        } catch (Exception e) {
            reasonJson = "[]";
        }

        // 계산 시각 (동일한 now 사용)
        Instant computedAt = now;

        return new Computed(base, icu, buck, adj, lagSec, stale, score, level, reasonJson, computedAt);
    }

    /**
     * nz (null-to-zero helper)
     *
     * - HospitalStatus의 Integer 필드들이 null일 수 있으므로 안전하게 0으로 치환.
     */
    private int nz(Integer n) {
        return n == null ? 0 : n;
    }

    @Getter
    @AllArgsConstructor
    public static class Computed {
        private short erBaseScore;
        private int icuAvail;
        private IcuBucket icuBucket;
        private short icuAdjust;
        private int lagSec;
        private StaleLevel staleLevel;
        private short score;
        private HliLevel level;
        private String reasonJson;

        private Instant computedAt;
    }
}
