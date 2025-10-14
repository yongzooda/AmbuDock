package com.example.ambudock.domain.disaster;

import com.example.ambudock.domain.common.enums.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="alert_keyword")
@Getter
@Setter
public class AlertKeyword {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=10)
    private Severity severity;

    @Column(nullable=false,length=300)
    private String pattern;

    @Column(nullable=false)
    private Boolean enabled=true;

    @Column(nullable=false)
    private Integer priority=100;

    @Column(nullable=false)
    private LocalDateTime updatedAt;
}