package com.example.ambudock.domain.region;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name="region")
@Getter
@Setter
public class Region {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long regionId;

    // 행정구역 코드 (법정동 코드 등)
    @Column(nullable=false,unique=true,length=20)
    private String code;

    @Column(nullable=false,length=255)
    private String nameFull;

    @Column(nullable=false,length=100)
    private String nameSido;

    @Column(length=100)
    private String nameSigungu;

    @Column(length=100)
    private String nameDong;

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
