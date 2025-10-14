package com.example.ambudock.domain.hospital;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
@Table(name="hospital_status")
@Getter
@Setter
//NMC(국립중앙의료원) 실시간 가용병상 정보 API 응답을 그대로 반영
public class HospitalStatus {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long statusId;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hpid", referencedColumnName="hpid")
    private Hospital hospital;

    // NMC 데이터의 공식 갱신 시각
    @Column(nullable=false)
    private LocalDateTime hvidate;

    //AmbuDock 서버가 해당 데이터를 수집(ingest)한 시각
    @Column(nullable=false)
    private LocalDateTime ingestedAt;

    //즉시 응급실 수용 가능한 병상 개수.
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
}
