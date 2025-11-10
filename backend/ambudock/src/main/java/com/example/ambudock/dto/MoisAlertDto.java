package com.example.ambudock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 원문 정규화 DTO (심각도/TTL 없음)
 * - 여기엔 판단 로직이 필요한 필드를 두지 않는다.
 * - 단, 원문에 있는 단계명/재난유형 같은 "힌트"는 보존(ingest 판단용).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoisAlertDto {

    @JsonProperty("md101Sn")
    private String md101Sn;          // 신/구 스펙 공통 식별자(SN 대체 포함)

    @JsonProperty("issuedAt")
    private Instant issuedAt;        // UTC Instant

    @JsonProperty("locationId")
    private String locationId;       // 없으면 null/UNKNOWN

    @JsonProperty("locationName")
    private String locationName;     // 수신지역명(원문)

    @JsonProperty("msg")
    private String msg;              // 본문(원문)

    // ---- 원문 힌트(ingest가 참조) ----
    @JsonProperty("emrgStepName")
    private String emrgStepName;     // EMRG_STEP_NM 등 단계명(있으면)

    @JsonProperty("hazardName")
    private String hazardName;       // DST_SE_NM 등 재난유형명(있으면)
}
