package com.example.ambudock.domain.hospital;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RankingEvent (추천/순위 계산 로그)
 *
 * - AmbuDock의 추천 알고리즘 블랙박스 로거.
 * - 사용자 요청 좌표(origin_lat, origin_lon) 기준으로
 *   추천이 수행된 시각, 후보 병원 수, 결과 상세 JSON, 수행시간(ms)을 기록함.
 */
@Entity
@Table(
        name = "ranking_event",
        indexes = {
                @Index(name = "IDX_ranking_event_calc_at", columnList = "calc_at")
        }
)
@Getter
@Setter
public class RankingEvent {

    /** 고유 로그 ID (자동 증가 PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    /** 추천 계산이 수행된 시각 (기본값: CURRENT_TIMESTAMP(3)) */
    @Column(name = "calc_at", nullable = false)
    private LocalDateTime calcAt;

    /** 요청 시 사용자의 위도 */
    @Column(name = "origin_lat", precision = 10, scale = 7, nullable = false)
    private BigDecimal originLat;

    /** 요청 시 사용자의 경도 */
    @Column(name = "origin_lon", precision = 10, scale = 7, nullable = false)
    private BigDecimal originLon;

    /** 이번 요청에서 평가한 병원 후보 수 (기본값 0) */
    @Column(name = "candidate_count", nullable = false)
    private Integer candidateCount = 0;

    /**
     * 추천 결과 상세 (JSON)
     * - TopN 병원 리스트, 각 후보의 거리, HLI 점수, 필터 사유 등을 포함.
     * - ex) {"top":[{"hpid":"A110001","score":89,"distance_km":2.4}],"filtered":3}
     */
    @Type(JsonType.class)
    @Column(name = "result_json", columnDefinition = "json", nullable = false)
    private String resultJson;

    /** 추천 연산 소요 시간(ms 단위) */
    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs = 0;
}
