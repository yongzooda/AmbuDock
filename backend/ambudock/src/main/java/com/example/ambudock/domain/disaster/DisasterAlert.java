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
//동일 재난문자가 중복 저장되지 않도록 md101_sn(재난문자 고유번호)에 UNIQUE 제약을 둠.
public class DisasterAlert {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long alertId;

    @Column(nullable=false,length=40)
    private String md101Sn; //API 수집 기준으로 재난문자 메시지의 식별자 역할을 함.

    @Column(nullable=false)
    private LocalDateTime issuedAt;

    @Column(nullable=false,length=40)
    private String locationId; //발령 지역의 행정 코드

    @Column(nullable=false,length=120)
    private String locationName;

    //재난문자 본문 내용 (Lob 사용: 최대 수천 자 가능)
    @Lob @Column(nullable=false)
    private String msg;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=10)
    private Severity severity;

    private LocalDateTime expiresAt;

    @Column(nullable=false)
    private Boolean isActive;
}
