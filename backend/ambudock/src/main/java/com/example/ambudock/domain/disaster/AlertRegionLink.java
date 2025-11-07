package com.example.ambudock.domain.disaster;

import com.example.ambudock.domain.common.enums.MatchLevel;
import com.example.ambudock.domain.region.Region;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name="alert_region_link",
        uniqueConstraints=@UniqueConstraint(name="UK_alert_region",columnNames={"alert_id","region_id"}))
@Getter
@Setter
// 각 연결은 어느 행정 단위까지 일치했는지를 나타내는 matchLevel(SIDO/SIGUNGU/DONG)을 가짐.
public class AlertRegionLink {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long alertRegionId;

    // 연결된 재난문자 엔티티 (N:1)
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="alert_id",nullable=false)
    private DisasterAlert alert;

    // 연결된 행정구역 엔티티 (N:1)
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="region_id",nullable=false)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(length=10)
    private MatchLevel matchLevel;

    /**
     * 처음 재난문자 수신 후 연동된 시점 (UTC Instant)
     * - DB: DATETIME(3)으로 밀리초 정밀도 보존 권장
     * - 엔티티가 persist 될 때 값이 비어있으면 현재 UTC 시각으로 채워집니다.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant createdAt; // 처음 재난문자 수신 후 연동된 시점

    /** 마지막 갱신 시각 (UTC Instant), 업데이트 시 자동 갱신 */
    @Column(name = "updated_at", columnDefinition = "DATETIME(3)")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
