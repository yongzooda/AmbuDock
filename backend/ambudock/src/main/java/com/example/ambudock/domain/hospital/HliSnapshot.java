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
//NMC 실시간 병상 데이터(HospitalStatus)를 기반으로 1분 주기 계산.
public class HliSnapshot {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long hliId;

    //병원 상태(HospitalStatus)와 1:1 연결.
    //한 개의 상태 레코드(status_id)당 하나의 HLI 결과만 존재.
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

    //ICU 보정 점수
    @Column(nullable=false)
    private Short icuAdjust;

    @Column(nullable=false)
    private Integer lagSec;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=10)
    private StaleLevel staleLevel;

    //최종 혼잡 점수 (0~100, clamp 적용 후)
    @Column(nullable=false)
    private Short score;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=10)
    private HliLevel level;

    /**
     * 점수 산출 근거를 JSON 형태로 저장.
     * - 예: {"er_base":70,"icu_adjust":-5,"lag_penalty":-3,"total":62}
     * - 분석 및 디버깅, 프론트 표시용.
     */
    @Type(JsonType.class)
    @Column(columnDefinition="json")
    private String reason; // JSON string
}