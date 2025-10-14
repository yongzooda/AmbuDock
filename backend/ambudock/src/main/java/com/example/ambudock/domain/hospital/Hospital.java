package com.example.ambudock.domain.hospital;

import com.example.ambudock.domain.region.Region;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="hospital")
@Getter
@Setter
public class Hospital {
    @Id

    @Column(length=20)
    private String hpid;

    @Column(nullable=false,length=255)
    private String name;

    @Column(precision=11,scale=8)
    private BigDecimal lat;

    @Column(precision=11,scale=8)
    private BigDecimal lon;

    @Column(length=255)
    private String addrRoad;

    @Column(length=255)
    private String addrJibun;

    @ManyToOne(fetch= FetchType.LAZY)
    @JoinColumn(name="region_id")
    private Region region;

    /** 응급실 전화번호 (dutyTel3) */
    @Column(length=50)
    private String telEmergency;

    /** 대표 전화번호 (있을 경우) */
    @Column(length=50)
    private String telMain;

    @Column(nullable=false)
    private LocalDateTime createdAt;

    @Column(nullable=false) private
    LocalDateTime updatedAt;
}