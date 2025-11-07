package com.example.ambudock.domain.disaster;

import com.example.ambudock.domain.common.enums.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name="disaster_alert",
        uniqueConstraints=@UniqueConstraint(name="UK_disaster_alert_md101_sn",columnNames="md101_sn"))
@Getter
@Setter
// 동일 재난문자가 중복 저장되지 않도록 md101_sn(재난문자 고유번호)에 UNIQUE 제약을 둠.
public class DisasterAlert {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long alertId;

    @Column(name = "md101_sn", nullable=false, length=40)
    private String md101Sn; // API 수집 기준으로 재난문자 메시지의 식별자 역할을 함.

    /**
     * 발행 시각 (원천)
     * - 외부에서 들어오는 문자열/로컬타임(KST 등)은 반드시 service/controller에서
     *   OffsetDateTime.parse(...).toInstant() 또는 withZoneSameInstant(UTC).toInstant()로 저장
     */
    @Column(name = "issued_at", nullable=false, columnDefinition = "DATETIME(3)")
    private Instant issuedAt;

    @Column(name = "location_id", nullable=false, length=40)
    private String locationId; // 발령 지역의 행정 코드

    @Column(name = "location_name", nullable=false, length=120)
    private String locationName;

    // 재난문자 본문 내용 (TEXT)
    @Lob
    @Column(name = "msg", nullable=false, columnDefinition = "text")
    private String msg;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=10)
    private Severity severity;

    @Column(name = "expires_at", columnDefinition = "DATETIME(3)")
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @PrePersist
    public void prePersist() {
        // isActive 기본값 보정 (null로 들어오는 경우 대비)
        if (this.isActive == null) {
            this.isActive = true;
        }
        // expiresAt/issuedAt은 원천 데이터가 중요하므로 자동 채우지 않음.
    }
}
