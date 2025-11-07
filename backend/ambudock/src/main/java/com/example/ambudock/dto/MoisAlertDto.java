package com.example.ambudock.dto;

import com.example.ambudock.domain.common.enums.Severity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MOIS(행안부 재난문자) 정규화 DTO
 * - 날짜/시간 필드는 Instant(UTC)로 통일합니다.
 * - JSON 입력은 가능한 한 ISO-8601 (예: "2025-11-07T14:30:00Z" 또는 "2025-11-07T23:30:00+09:00") 형태를 권장합니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoisAlertDto {

    @JsonProperty("md101Sn")
    private String md101Sn;

    /**
     * 발행시각 — UTC Instant 로 처리
     */
    @JsonProperty("issuedAt")
    private Instant issuedAt;

    @JsonProperty("locationId")
    private String locationId;

    @JsonProperty("locationName")
    private String locationName;

    @JsonProperty("msg")
    private String msg;

    /**
     * 서버(또는 정규화 레이어)에서 "IGNORE"/"WARN"/"BLOCK" 문자열을 ENUM으로 파싱해 올 것을 기대.
     */
    @JsonProperty("severity")
    private Severity severity;

    @JsonProperty("expiresAt")
    private Instant expiresAt;
}
