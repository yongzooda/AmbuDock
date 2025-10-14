package com.example.ambudock.repository;

import com.example.ambudock.domain.disaster.AlertRegionLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlertRegionLinkRepository extends JpaRepository<AlertRegionLink, Long> {
    @Query("""
    select l from AlertRegionLink l
    join fetch l.alert a
    where a.isActive = true and l.region.regionId = :regionId
    order by a.severity desc, a.issuedAt desc
  """)
    List<AlertRegionLink> findActiveByRegion(@Param("regionId") Long regionId);
}
