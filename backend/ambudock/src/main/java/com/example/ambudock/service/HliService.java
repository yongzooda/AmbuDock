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

    private IcuBucket bucket(int avail) {
        if (avail <= 0) return IcuBucket.NONE;
        if (avail == 1) return IcuBucket.CRITICAL_LOW;
        if (avail <= 3) return IcuBucket.LOW;
        if (avail <= 9) return IcuBucket.MID;
        return IcuBucket.HIGH;
    }

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

    public Computed compute(HospitalStatus s, int soft, int hard, int lagPenalty) {
        int icu = nz(s.getHv2()) + nz(s.getHv3()) + nz(s.getHv4())
                + nz(s.getHv5()) + nz(s.getHv6()) + nz(s.getHvoc());
        IcuBucket buck = bucket(icu);
        short base = erBaseScore(s.getHvec());
        short adj = adjust(buck);

        Instant hviInstant = (s.getHvidate() == null) ? Instant.EPOCH : s.getHvidate();

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        int lagSec = (int) Duration.between(hviInstant, now).getSeconds();
        StaleLevel stale = lagSec < soft ? StaleLevel.FRESH :
                (lagSec < hard ? StaleLevel.SOFT : StaleLevel.HARD);

        short score;
        HliLevel level;

        if (stale == StaleLevel.HARD) {
            score = 0;
            level = HliLevel.GRAY;
        } else {
            int tmp = base + adj;
            if (stale == StaleLevel.SOFT) tmp -= lagPenalty;
            tmp = Math.min(100, Math.max(0, tmp));
            score = (short) tmp;

            if (s.getHvec() == 0) level = HliLevel.RED;
            else if (score >= 70) level = HliLevel.GREEN;
            else if (score >= 40) level = HliLevel.YELLOW;
            else level = HliLevel.RED;
        }

        List<String> reason = List.of(
                "ER=" + s.getHvec() + " → base=" + base,
                "ICU=" + icu + " → " + buck + " → adj=" + adj,
                "lag=" + lagSec + "s → " + stale +
                        (stale == StaleLevel.SOFT ? " → -" + lagPenalty : "")
        );

        String reasonJson;
        try {
            reasonJson = om.writeValueAsString(reason);
        } catch (Exception e) {
            reasonJson = "[]";
        }

        // set computedAt to current UTC instant (ms precision)
        Instant computedAt = now;

        return new Computed(base, icu, buck, adj, lagSec, stale, score, level, reasonJson, computedAt);
    }

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
