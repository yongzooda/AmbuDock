package com.example.ambudock.domain.disaster;

import com.example.ambudock.domain.common.enums.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="disaster_alert",
        uniqueConstraints=@UniqueConstraint(name="UK_disaster_alert_md101_sn",columnNames="md101_sn"))
@Getter
@Setter
public class DisasterAlert {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long alertId;

    @Column(nullable=false,length=40)
    private String md101Sn;

    @Column(nullable=false)
    private LocalDateTime issuedAt;

    @Column(nullable=false,length=40)
    private String locationId;

    @Column(nullable=false,length=120)
    private String locationName;

    @Lob @Column(nullable=false)
    private String msg;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=10)
    private Severity severity;

    private LocalDateTime expiresAt;

    @Column(nullable=false)
    private Boolean isActive;
}
