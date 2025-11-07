package com.example.ambudock.domain.config;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_config")
@Getter @Setter
public class AppConfig {

    @Id
    @Column(name = "cfg_key", length = 60, nullable = false)
    private String cfgKey;

    @Column(name = "cfg_value", length = 120, nullable = false)
    private String value;

    @Column(name = "description", length = 200)
    private String description;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
