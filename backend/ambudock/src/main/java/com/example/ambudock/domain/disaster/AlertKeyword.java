package com.example.ambudock.domain.disaster;

import com.example.ambudock.domain.common.enums.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name="alert_keyword")
@Getter
@Setter
public class AlertKeyword {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long ruleId; /** 규칙 고유 식별자 (자동 증가) */

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=10)
    private Severity severity; // 규칙이 감지될 경우 적용할 심각도 등급

    @Column(nullable=false,length=300)
    private String pattern; //재난문자 본문(msg)에서 탐지할 정규식 패턴

    @Column(nullable=false)
    private Boolean enabled = true; //규칙 활성 여부 (false일 경우 탐지 대상에서 제외)

    //규칙 우선순위 (낮을수록 우선 적용)
    //BLOCK(10) < WARN(50) < IGNORE(100)
    @Column(nullable=false)
    private Integer priority = 100;

    /**
     * 규칙의 최종 수정 시각 (UTC Instant)
     */
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
