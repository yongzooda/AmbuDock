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
public class HospitalStatus {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long statusId;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hpid", referencedColumnName="hpid")
    private Hospital hospital;

    @Column(nullable=false)
    private LocalDateTime hvidate;

    @Column(nullable=false)
    private LocalDateTime ingestedAt;

    @Column(nullable=false)
    private Integer hvec;
    private Integer hv2;
    private Integer hv3;
    private Integer hv4;
    private Integer hv5;
    private Integer hv6;
    private Integer hvoc;
}
