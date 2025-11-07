package com.example.ambudock.domain.hospital;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name="hospital_status")
@Getter
@Setter
// NMC(국립중앙의료원) 실시간 가용병상 정보 API 응답을 그대로 반영
public class HospitalStatus {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long statusId;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hpid", referencedColumnName="hpid")
    private Hospital hospital;

    /**
     * NMC 데이터의 공식 갱신 시각 (원천)
     * -> **중요**: 외부에서 오는 문자열/로컬시간(KST) 등은 수신부에서
     *    OffsetDateTime.parse(...).toInstant() 또는 withZoneSameInstant(UTC).toInstant()
     *    로 변환한 후 이 필드에 설정해야 합니다.
     */
    @Column(name = "hvidate", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant hvidate;

    /**
     * AmbuDock 서버가 해당 데이터를 수집(ingest)한 시각 (UTC Instant)
     * - 엔티티 persist 시 값이 비어있다면 자동으로 현재 UTC 시각으로 채워집니다.
     */
    @Column(name = "ingested_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant ingestedAt;

    // 즉시 응급실 수용 가능한 병상 개수.
    @Column(nullable=false)
    private Integer hvec;

    /** 내과중환자실 가용 병상 */
    private Integer hv2;
    /** 외과중환자실 가용 병상 */
    private Integer hv3;
    /** 정형외과 입원실 가용 병상 */
    private Integer hv4;
    /** 신경과 입원실 가용 병상  */
    private Integer hv5;
    /** 신경외과중환자실 가용 병상  */
    private Integer hv6;
    /** 수술실 가용 병상 */
    private Integer hvoc;

    @PrePersist
    public void prePersist() {
        if (this.ingestedAt == null) {
            this.ingestedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        }
    }
}
