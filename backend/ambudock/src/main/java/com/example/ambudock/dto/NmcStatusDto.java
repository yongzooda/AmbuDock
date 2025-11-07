package com.example.ambudock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * NMC(실시간 병상) API에서 받아올 최소 데이터 구조
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NmcStatusDto {
    @JsonProperty("hpid")
    private String hpid;

    @JsonProperty("hvidate")
    private Instant hvidate;

    @JsonProperty("hvec")
    private Integer hvec;

    @JsonProperty("hv2")
    private Integer hv2;

    @JsonProperty("hv3")
    private Integer hv3;

    @JsonProperty("hv4")
    private Integer hv4;

    @JsonProperty("hv5")
    private Integer hv5;

    @JsonProperty("hv6")
    private Integer hv6;

    @JsonProperty("hvoc")
    private Integer hvoc;
}
