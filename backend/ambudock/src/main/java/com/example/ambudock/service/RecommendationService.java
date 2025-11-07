package com.example.ambudock.service;

import com.example.ambudock.domain.common.enums.HliLevel;
import com.example.ambudock.domain.common.enums.Severity;
import com.example.ambudock.domain.hospital.HliSnapshot;
import com.example.ambudock.domain.hospital.Hospital;
import com.example.ambudock.domain.hospital.HospitalStatus;
import com.example.ambudock.domain.hospital.RankingEvent;
import com.example.ambudock.dto.disaster.DisasterDtos;
import com.example.ambudock.dto.hospital.HospitalDtos;
import com.example.ambudock.dto.recommendation.RecommendationDtos;
import com.example.ambudock.dto.tmap.TmapDtos;
import com.example.ambudock.repository.HliSnapshotRepository;
import com.example.ambudock.repository.HospitalRepository;
import com.example.ambudock.repository.HospitalStatusRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Top-N 응급실 추천 서비스 (최종기획서 준수)
 * 1) 후보 K 확보(반경) → 2) HLI 신선도 1차 필터 → 3) 재난문자 2차 필터
 * 4) TMAP Proxy(전 후보) → 5) 정렬(level>score>severity>ETA>name)
 * 6) TopN + 로깅(RankingEvent)
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final HospitalRepository hospitalRepo;
    private final HospitalStatusRepository statusRepo;
    private final HliSnapshotRepository hliRepo;

    private final HliService hliService;
    private final DisasterService disasterService;
    private final TmapProxyService tmapProxyService;
    private final AppConfigService cfg;
    private final EntityManager em;

    /**
     * 추천 TopN 산출
     * - center: "lat,lon"
     * - radiusKmOpt: optional 반경 (null이면 app 기본 사용)
     * - topnOpt: optional topN override
     * - clientIp: 요청자 IP (로깅/추후 정책용)
     */
    @Transactional(readOnly = true)
    public RecommendationDtos.TopResponse recommendTop(String center,
                                                       Double radiusKmOpt,
                                                       Integer topnOpt,
                                                       String clientIp) {
        long t0 = System.currentTimeMillis();

        // ---- AppConfig (기본값 포함) ----
        int softSec = cfg.getInt("STALE_SOFT_SEC", 300);
        int hardSec = cfg.getInt("STALE_HARD_SEC", 3600);
        int lagPenalty = cfg.getInt("HLI_LAG_SOFT_PENALTY", 15);
        int warnPenalty = cfg.getInt("ALERT_WARN_PENALTY", 5); // 로그용 감점

        int K = cfg.getInt("CANDIDATE_K", 10);
        int Kmin = cfg.getInt("CANDIDATE_MIN", 5);
        double defaultRadius = Double.parseDouble(cfg.get("default-radius-km", "15"));
        double radiusKm = (radiusKmOpt != null) ? radiusKmOpt : defaultRadius;

        int topN = (topnOpt != null && topnOpt > 0) ? topnOpt : cfg.getInt("RANK_TOPN", 3);

        // ---- 입력 파싱 ----
        String[] parts = center.split(",");
        double originLat = Double.parseDouble(parts[0].trim());
        double originLon = Double.parseDouble(parts[1].trim());

        // =========================================================
        // 후보 풀 생성 — 반경 내 거리순(하버사인)으로 K개 확보 (부족 시 반경 넘어도 Kmin까지)
        // =========================================================
        List<Object[]> rows = hospitalRepo.searchWithDistance(originLat, originLon);
        List<Hospital> prelim = new ArrayList<>();
        Map<String, Double> lineDistByHpid = new HashMap<>();
        for (Object[] r : rows) {
            Hospital h = (Hospital) r[0];
            double d = ((Number) r[1]).doubleValue();
            if (d > radiusKm && prelim.size() >= Kmin) break;
            lineDistByHpid.put(h.getHpid(), d);
            prelim.add(h);
            if (prelim.size() >= K) break;
        }
        if (prelim.isEmpty()) return new RecommendationDtos.TopResponse(List.of());

        // =========================================================
        // 1차 필터 — HLI 신선도: HARD 제외, SOFT 유지 (스냅샷 우선, 없으면 즉석 compute)
        // =========================================================
        record HliPack(Hospital h, HospitalDtos.HliSummary hli, short score, HliLevel level) {}
        List<HliPack> afterHli = new ArrayList<>();
        int filteredByHli = 0;

        for (Hospital h : prelim) {
            HospitalStatus latest = statusRepo.findLatestByHpid(h.getHpid(), PageRequest.of(0, 1))
                    .stream().findFirst().orElse(null);

            HliSnapshot snap = null;
            HliService.Computed comp = null;

            if (latest != null) {
                snap = hliRepo.findByStatus_StatusId(latest.getStatusId()).orElse(null);
                if (snap == null) comp = hliService.compute(latest, softSec, hardSec, lagPenalty);
            }

            HospitalDtos.HliSummary summary = null;
            short score = -1;
            HliLevel level = HliLevel.GRAY;

            if (snap != null) {
                if ("HARD".equals(snap.getStaleLevel().name())) { filteredByHli++; continue; }

                // --- DTOs are UTC(Instant) per user's note: pass Instant directly ---
                summary = HospitalDtos.HliSummary.builder()
                        .score(snap.getScore())
                        .level(snap.getLevel().name())
                        .color(colorOf(snap.getLevel()))
                        .lagSec(snap.getLagSec())
                        .staleLevel(snap.getStaleLevel().name())
                        .updatedAt(snap.getCalcAt()) // Instant
                        .build();
                score = snap.getScore();
                level = snap.getLevel();
            } else if (comp != null) {
                if ("HARD".equals(comp.getStaleLevel().name())) { filteredByHli++; continue; }

                Instant updatedAt = (comp.getComputedAt() != null)
                        ? comp.getComputedAt()
                        : Instant.now().truncatedTo(ChronoUnit.MILLIS);

                summary = HospitalDtos.HliSummary.builder()
                        .score(comp.getScore())
                        .level(comp.getLevel().name())
                        .color(colorOf(comp.getLevel()))
                        .lagSec(comp.getLagSec())
                        .staleLevel(comp.getStaleLevel().name())
                        .updatedAt(updatedAt) // Instant
                        .build();
                score = comp.getScore();
                level = comp.getLevel();
            } else {
                filteredByHli++; continue; // 상태 자체 없음
            }
            afterHli.add(new HliPack(h, summary, score, level));
        }
        if (afterHli.isEmpty()) return new RecommendationDtos.TopResponse(List.of());

        // =========================================================
        // 2차 필터 — 재난문자: BLOCK 제외, WARN은 배지/로그 감점만
        // =========================================================
        List<String> hpidsAfterHli = afterHli.stream().map(p -> p.h.getHpid()).toList();
        DisasterDtos.EvaluateResponse eval = disasterService.evaluate(hpidsAfterHli);
        Map<String, DisasterDtos.EvaluateItem> evalMap =
                eval.getResults().stream().collect(Collectors.toMap(DisasterDtos.EvaluateItem::getHpid, x -> x));

        List<HliPack> afterDisaster = new ArrayList<>();
        int filteredByDisaster = 0;
        for (HliPack p : afterHli) {
            DisasterDtos.EvaluateItem e = evalMap.get(p.h.getHpid());
            if (e != null && e.isBlocked()) { filteredByDisaster++; continue; } // BLOCK 제외
            afterDisaster.add(p);
        }
        if (afterDisaster.isEmpty()) return new RecommendationDtos.TopResponse(List.of());

        // =========================================================
        // 3) TMAP Proxy — 2차 필터 통과 후보 전체 호출(차량/도보 동시)
        // =========================================================
        TmapDtos.RouteRequest routeReq = TmapDtos.RouteRequest.builder()
                .origin(center) // "lat,lon"
                .options(TmapDtos.RouteRequest.Options.builder().car(true).walk(true).build())
                .build();

        List<TmapDtos.RouteRequest.Candidate> cands = new ArrayList<>(afterDisaster.size());
        for (HliPack p : afterDisaster) {
            cands.add(TmapDtos.RouteRequest.Candidate.builder()
                    .hpid(p.h.getHpid())
                    .lat(p.h.getLat())
                    .lon(p.h.getLon())
                    .build());
        }
        routeReq.setCandidates(cands);

        TmapDtos.RouteResponse routeRes;
        try {
            routeRes = tmapProxyService.routeForCandidates(routeReq, clientIp);
        } catch (Exception e) {
            routeRes = TmapDtos.RouteResponse.builder()
                    .items(List.of()).durationMs(0L).source("ESTIMATE").cached(0).build();
        }
        Map<String, TmapDtos.Result> tmapByHpid =
                (routeRes.getItems() == null) ? Map.of()
                        : routeRes.getItems().stream()
                        .collect(Collectors.toMap(TmapDtos.Result::getHpid, x -> x));

        // =========================================================
        // 4) 합성 → 카드 DTO 구성 + final_score(로그 전용) 산출
        // =========================================================
        List<RecommendationDtos.TopItem> cards = new ArrayList<>();

        for (HliPack p : afterDisaster) {
            Hospital h = p.h;
            double lineKm = lineDistByHpid.getOrDefault(h.getHpid(), 0d);

            // 재난 경고(enum으로 통일: null/빈 → IGNORE)
            var e = evalMap.get(h.getHpid());
            Severity severity = (e != null) ? toSeverity(e.getSeverity()) : Severity.IGNORE;
            List<String> badges = (e != null && e.getBadges() != null) ? e.getBadges() : List.of();

            // TMAP or 추정치
            double etaCarMin, etaWalkMin, routeCarKm, routeWalkKm;
            String src;
            var r = tmapByHpid.get(h.getHpid());

            if (r != null) {
                etaCarMin  = (r.getEta_car_min()  == null ? estimateCar(lineKm)  : r.getEta_car_min());
                etaWalkMin = (r.getEta_walk_min() == null ? estimateWalk(lineKm) : r.getEta_walk_min());
                routeCarKm = (r.getRoute_car_km() == null ? lineKm : r.getRoute_car_km());
                routeWalkKm= (r.getRoute_walk_km()== null ? lineKm : r.getRoute_walk_km());
                Double lineFromTmap = r.getDistance_line_km();
                if (lineFromTmap != null) lineKm = lineFromTmap;
                src = (r.getSource() != null) ? r.getSource()
                        : (routeRes.getSource() == null ? "TMAP" : routeRes.getSource());
            } else {
                etaCarMin = estimateCar(lineKm);
                etaWalkMin = estimateWalk(lineKm);
                routeCarKm = lineKm;
                routeWalkKm = lineKm;
                src = "ESTIMATE";
            }

            // final_score = HLI - WARN(로그감점) - line_km*1.5 - car_min/10 (로그/근거용)
            double disasterPenalty = (severity == Severity.WARN) ? warnPenalty : 0;
            double distancePenalty = lineKm * 1.5;
            double etaPenalty = etaCarMin / 10.0;
            double baseScore = Math.max(0, p.score);
            double finalScore = baseScore - disasterPenalty - distancePenalty - etaPenalty;

            List<String> reason = new ArrayList<>();
            reason.add("HLI=" + baseScore + ", level=" + p.level.name());
            if (severity == Severity.WARN) reason.add("WARN -" + warnPenalty);
            reason.add(String.format("line=%.2fkm → -%.2f", lineKm, distancePenalty));
            reason.add(String.format("car=%.1fmin → -%.1f", etaCarMin, etaPenalty));
            reason.add(String.format("final=%.1f", finalScore));

            cards.add(RecommendationDtos.TopItem.builder()
                    .rank(0)
                    .hpid(h.getHpid()).name(h.getName())
                    .lat(h.getLat()).lon(h.getLon())
                    .addr_road(h.getAddrRoad())
                    .tel_emergency(h.getTelEmergency())
                    .hli(p.hli)
                    .severity(severity)
                    .badges(badges)
                    .eta_car_min(etaCarMin)
                    .eta_walk_min(etaWalkMin)
                    .distance_line_km(lineKm)
                    .route_car_km(routeCarKm)
                    .route_walk_km(routeWalkKm)
                    .eta_source(src)
                    .final_score(finalScore)
                    .reason(reason)
                    .build());
        }

        // =========================================================
        // 5) 정렬: level > score > severity(IGNORE<WARN<BLOCK) > ETA(car) > name
        // =========================================================
        cards.sort((a, b) -> {
            int byLevel = Integer.compare(levelRank(a.getHli()), levelRank(b.getHli()));
            if (byLevel != 0) return byLevel;

            int s1 = (a.getHli() != null) ? a.getHli().getScore() : -1;
            int s2 = (b.getHli() != null) ? b.getHli().getScore() : -1;
            if (s1 != s2) return Integer.compare(s2, s1); // desc

            int sev1 = severityRank(a.getSeverity());
            int sev2 = severityRank(b.getSeverity());
            if (sev1 != sev2) return Integer.compare(sev1, sev2); // IGNORE(0) 먼저

            if (Double.compare(a.getEta_car_min(), b.getEta_car_min()) != 0)
                return Double.compare(a.getEta_car_min(), b.getEta_car_min()); // asc

            return a.getName().compareTo(b.getName());
        });

        for (int i = 0; i < cards.size(); i++) cards.get(i).setRank(i + 1);
        List<RecommendationDtos.TopItem> top = cards.stream().limit(topN).toList();

        // =========================================================
        // 6) 로깅 (RankingEvent)
        // =========================================================
        RankingEvent evt = new RankingEvent();

        // DTOs/Entities using Instant: record UTC Instant with ms precision
        Instant nowInstant = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        evt.setCalcAt(nowInstant); // assumes RankingEvent.calcAt is Instant in entity

        evt.setOriginLat(BigDecimal.valueOf(originLat));
        evt.setOriginLon(BigDecimal.valueOf(originLon));
        evt.setCandidateCount(cards.size());
        evt.setResultJson(buildLogJson(top, filteredByHli, filteredByDisaster));
        evt.setDurationMs((int) (System.currentTimeMillis() - t0));
        em.persist(evt);

        return new RecommendationDtos.TopResponse(top);
    }

    // --------- helpers ---------
    private double estimateWalk(double lineKm) { return (lineKm / 4.5) * 60.0; } // 4.5km/h
    private double estimateCar(double lineKm)  { return (lineKm / 30.0) * 60.0; } // 30km/h

    private int levelRank(HospitalDtos.HliSummary h) {
        if (h == null || h.getLevel() == null) return 9;
        return switch (h.getLevel()) {
            case "GREEN" -> 1;
            case "YELLOW" -> 2;
            case "RED" -> 3;
            case "GRAY" -> 4;
            default -> 9;
        };
    }

    /** IGNORE(0) < WARN(1) < BLOCK(2) — enum 기반 우선순위 */
    private int severityRank(Severity s) {
        if (s == null) return 0;
        return switch (s) {
            case IGNORE -> 0;
            case WARN -> 1;
            case BLOCK -> 2;
        };
    }

    private String colorOf(HliLevel lvl) {
        return switch (lvl) {
            case GREEN -> "#16a34a";
            case YELLOW -> "#eab308";
            case RED -> "#ef4444";
            case GRAY -> "#9ca3af";
        };
    }

    private String esc(String s) { return s == null ? "" : s.replace("\"", "\\\""); }

    // 문자열 → enum (안전 변환, 잘못된 값/널은 IGNORE)
    private Severity toSeverity(String s) {
        if (s == null || s.isBlank()) return Severity.IGNORE;
        try { return Severity.valueOf(s); }
        catch (Exception ex) { return Severity.IGNORE; }
    }

    private String buildLogJson(List<RecommendationDtos.TopItem> top, int filteredHli, int filteredDisaster) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"filtered_hli\":").append(filteredHli)
                .append(",\"filtered_disaster\":").append(filteredDisaster)
                .append(",\"top\":[");
        for (int i = 0; i < top.size(); i++) {
            var x = top.get(i);
            sb.append("{\"rank\":").append(x.getRank())
                    .append(",\"hpid\":\"").append(esc(x.getHpid())).append("\"")
                    .append(",\"score\":").append(x.getHli() != null ? x.getHli().getScore() : -1)
                    .append(",\"level\":\"").append(x.getHli() != null ? x.getHli().getLevel() : "").append("\"")
                    .append(",\"severity\":\"").append(x.getSeverity() == null ? "" : x.getSeverity().name()).append("\"")
                    .append(",\"eta_car_min\":").append(String.format("%.1f", x.getEta_car_min()))
                    .append(",\"line_km\":").append(String.format("%.2f", x.getDistance_line_km()))
                    .append(",\"final_score\":").append(String.format("%.1f", x.getFinal_score()))
                    .append(",\"reason\":[");
            for (int j = 0; j < x.getReason().size(); j++) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(esc(x.getReason().get(j))).append("\"");
            }
            sb.append("]}");
            if (i < top.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }
}
