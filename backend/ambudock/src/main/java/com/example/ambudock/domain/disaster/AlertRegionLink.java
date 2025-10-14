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
public class AlertRegionLink {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long alertRegionId;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="alert_id",nullable=false)
    private DisasterAlert alert;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="region_id",nullable=false)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(length=10)
    private MatchLevel matchLevel;

    @Column(nullable=false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
