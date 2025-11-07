package com.example.ambudock.dto.hospital;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class HospitalDtos {
    @Getter
    @Builder
    public static class HliSummary {
        private short score;
        private String level;
        private String color;
        private int lagSec;
        private String staleLevel;
        private Instant updatedAt;    // 변경: LocalDateTime -> Instant (UTC)
    }

    @Getter @Builder
    public static class HospitalListItem {
        private String hpid;
        private String name;
        private BigDecimal lat;
        private BigDecimal lon;
        private String addrRoad;
        private String telEmergency;
        private double distanceKm;
        private HliSummary hli;
    }

    @Getter
    public static class NearbyRequest {
        @NotBlank
        private String center;       // "lat,lon"
        private Double radius_km;
    }

    @Getter @AllArgsConstructor
    public static class NearbyResponse {
        private List<HospitalListItem> items;
    }
}
