package com.example.ambudock.domain.disaster;

import com.example.ambudock.domain.common.enums.MatchLevel;
import com.example.ambudock.domain.region.Region;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="alert_region_link",
        uniqueConstraints=@UniqueConstraint(name="UK_alert_region",columnNames={"alert_id","region_id"}))
@Getter
@Setter
//각 연결은 어느 행정 단위까지 일치했는지를 나타내는 matchLevel(SIDO/SIGUNGU/DONG)을 가짐.
public class AlertRegionLink {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long alertRegionId;

    //연결된 재난문자 엔티티 (N:1)
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="alert_id",nullable=false)
    private DisasterAlert alert;

    //연결된 행정구역 엔티티 (N:1)
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="region_id",nullable=false)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(length=10)
    private MatchLevel matchLevel;

    @Column(nullable=false)
    private LocalDateTime createdAt; //처음 재난문자 수신 후 연동된 시점
    private LocalDateTime updatedAt;
}
