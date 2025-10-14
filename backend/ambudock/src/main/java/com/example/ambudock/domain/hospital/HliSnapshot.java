package com.example.ambudock.domain.hospital;

import com.example.ambudock.domain.common.enums.HliLevel;
import com.example.ambudock.domain.common.enums.IcuBucket;
import com.example.ambudock.domain.common.enums.StaleLevel;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

@Entity
@Table(name="hli_snapshot",
        uniqueConstraints = @UniqueConstraint(name="UK_hli_status",columnNames="status_id"))
@Getter
@Setter
public class HliSnapshot {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long hliId;

    @OneToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="status_id",nullable=false)
    private HospitalStatus status;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hpid", referencedColumnName="hpid",nullable=false)
    private Hospital hospital;

    @Column(nullable=false)
    private LocalDateTime calcAt;

    @Column(nullable=false)
    private Short erBaseScore;

    @Column(nullable=false)
    private Integer icuAvail;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=20)
    private IcuBucket icuBucket;

    @Column(nullable=false)
    private Short icuAdjust;

    @Column(nullable=false)
    private Integer lagSec;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=10)
    private StaleLevel staleLevel;

    @Column(nullable=false)
    private Short score;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=10)
    private HliLevel level;

    @Type(JsonType.class)
    @Column(columnDefinition="json")
    private String reason; // JSON string
}