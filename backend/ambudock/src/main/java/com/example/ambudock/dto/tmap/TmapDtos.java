package com.example.ambudock.dto.tmap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

public class TmapDtos {

    /* ===================== 요청 DTO ===================== */

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RouteRequest {
        /** 사용자(현위치) 좌표: "lat,lon" 형태 */
        @NotBlank
        private String origin;

        /** 후보 병원 K개 (K ≤ 10) */
        @NotEmpty
        @Singular("candidate")
        private List<Candidate> candidates;

        /** 옵션: 차량/도보 동시 포함 여부 (기본 true/true) */
        @NotNull
        @Builder.Default
        private Options options = new Options();

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class Candidate {
            /** 병원 식별자 (NMC hpid) */
            @NotBlank private String hpid;
            /** 병원 위도/경도 (WGS84, 소수점) */
            @NotNull  private BigDecimal lat;
            @NotNull  private BigDecimal lon;
        }

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class Options {
            /** 차량 ETA/거리 계산 여부 (기본 true) */
            @Builder.Default private boolean car = true;
            /** 도보 ETA/거리 계산 여부 (기본 true) */
            @Builder.Default private boolean walk = true;
        }
    }

    /* ===================== 응답 DTO ===================== */

    @Getter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class RouteResponse {
        /** 후보 K개 각각의 결과 리스트 */
        private List<Result> items;
        /** 전체 처리 소요 시간(ms) */
        private Long durationMs;
        /** 전체 응답의 근거: "TMAP" 또는 "ESTIMATE" */
        private String source;
        /** 캐시 적중 개수(옵션, 없으면 null) */
        private Integer cached;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Result {
        private String hpid;

        // ETA(분) — 모드 미계산 시 null 가능
        private Double eta_car_min; // 차량 ETA(분)
        private Double eta_walk_min; // 도보 ETA(분)

        // 거리(km) — 모드 미계산 시 null 가능
        private Double distance_line_km;  // 직선거리
        private Double route_car_km;      // 차량 경로거리
        private Double route_walk_km;     // 도보 경로거리

        /** 각 결과의 근거: "TMAP" | "ESTIMATE" */
        private String source;
    }

    /* ===================== 에러 바디(옵션) ===================== */

    @Getter @AllArgsConstructor
    public static class ErrorBody {
        /** 앱 내부 에러 코드(예: TMAP_TIMEOUT, INVALID_ORIGIN 등) */
        private String code;
        /** 사용자/로그용 메시지 */
        private String message;
    }
}
