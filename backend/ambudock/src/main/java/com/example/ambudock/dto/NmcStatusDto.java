package com.example.ambudock.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * NEMC(실시간 병상) API에서 받아올 최소 데이터 구조
 *
 * - 병원 마스터 온보딩을 위해 병원명(dutyname), 응급실 전화(dutytel3) 포함
 * - hvidate는 수신부에서 반드시 UTC Instant로 역직렬화되도록 한다.
 *   (원천이 문자열 ISO-8601이면 @JsonFormat 그대로 사용)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NmcStatusDto {

    @JsonProperty("hpid")
    private String hpid;

    // 온보딩용 병원 메타
    @JsonProperty("dutyname")
    private String dutyname;     // 병원명 (예: "삼성서울병원")

    @JsonProperty("dutytel3")
    private String dutytel3;     // 응급실 전화

    // 원천이 문자열 ISO 타임스탬프라면 그대로 Instant로 파싱됨 (예: "2025-11-07T10:15:30Z")
    @JsonProperty("hvidate")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
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
