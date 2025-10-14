package com.example.ambudock.dto.disaster;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

public class DisasterDtos {
    @Getter
    public static class EvaluateRequest {
        @NotNull
        @Size(min=1) private List<String> hpids;
    }

    @Getter @AllArgsConstructor
    public static class EvaluateItem {
        private String hpid;
        private boolean blocked;
        private List<String> badges;
        private String severity; // WARN/BLOCK/null
    }

    @Getter @AllArgsConstructor
    public static class EvaluateResponse {
        private List<EvaluateItem> results;
    }
}

