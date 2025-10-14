package com.example.ambudock.domain.region;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
@Table(name="region")
@Getter
@Setter
public class Region {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long regionId;

    //행정구역 코드 (법정동 코드 등)
    @Column(nullable=false,unique=true,length=20)
    private String code;

    @Column(nullable=false,length=255)
    private String nameFull;

    @Column(nullable=false,length=100)
    private String nameSido;

    @Column(length=100)
    private String nameSigungu;

    @Column(length=100)
    private String nameDong;

    @Column(nullable=false)
    private LocalDateTime createdAt;

    @Column(nullable=false)
    private LocalDateTime updatedAt;
}
