package com.example.ambudock.repository;

import com.example.ambudock.domain.region.Region;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface RegionRepository extends JpaRepository<Region, Long> {
    Optional<Region> findByCode(String code);
    boolean existsByCode(String code);

    // 정확 일치 우선
    @Query("""
        select r from Region r
        where r.nameSido = :sido
          and (:sgg is null or r.nameSigungu = :sgg)
          and (:dong is null or r.nameDong = :dong)
    """)
    List<Region> findByNames(
            @Param("sido") String sido,
            @Param("sgg") String sigungu,
            @Param("dong") String dong
    );

    // 시/군/구 레벨까지만 매칭할 때
    @Query("""
        select r from Region r
        where r.nameSido = :sido
          and r.nameSigungu = :sgg
    """)
    List<Region> findBySidoAndSigungu(@Param("sido") String sido, @Param("sgg") String sgg);

    // 시/도 레벨
    List<Region> findByNameSido(String nameSido);
}
