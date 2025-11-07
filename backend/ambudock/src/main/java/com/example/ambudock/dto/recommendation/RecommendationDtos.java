package com.example.ambudock.dto.recommendation;

import com.example.ambudock.domain.common.enums.Severity;
import com.example.ambudock.dto.hospital.HospitalDtos.HliSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Top3 응급실 추천 응답 DTO
 * - 카드 바인딩 필드
 * - final_score, reason[]은 로그/근거 표시용 (정렬 근거는 서비스 로직에서 결정)
 */
public class RecommendationDtos {

    @Getter @Builder
    public static class TopItem {
        @Setter                 // rank는 서비스에서 후보를 정렬한 뒤 세팅
        private int rank;

        private String hpid;
        private String name;
        private BigDecimal lat;
        private BigDecimal lon;
        private String addr_road;
        private String tel_emergency;

        // 병원 혼잡 지수 요약 — NMC/HLI 관련 요약 객체
        private HliSummary hli;

        // 재난 경고
        private Severity severity;      // IGNORE | WARN | BLOCK
        private List<String> badges;     // UI에 표시할 경고 배지들(예: ["침수주의","대기중"])

        // ETA / 거리 (TMAP Proxy 또는 ESTIMATE 결과)
        private double eta_car_min;
        private double eta_walk_min;
        private double distance_line_km;
        private double route_car_km;
        private double route_walk_km;
        private String eta_source;       // "TMAP" | "ESTIMATE" — 어떤 근거로 나왔는지 표시

        // 로그/근거
        private double final_score;      // '표시용 근거값'으로 저장(실제 우선순위는 서비스 규칙 따름)
        private List<String> reason;     // 감점·신선·경고 등 근거 문구
    }

    @Getter @AllArgsConstructor
    public static class TopResponse {
        private List<TopItem> items; // 최종 Top 3 리스트
    }
}
