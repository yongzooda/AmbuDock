package com.example.ambudock.domain.hospital;

import com.example.ambudock.domain.region.Region;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    /**
     * 생성/수정 시각은 UTC Instant로 저장됩니다.
     * DB는 DATETIME(3)으로 정밀도(밀리초)를 보존하도록 columnDefinition 명시.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
