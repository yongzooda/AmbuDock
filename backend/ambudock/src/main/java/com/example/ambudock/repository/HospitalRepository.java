package com.example.ambudock.repository;

import com.example.ambudock.domain.hospital.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HospitalRepository extends JpaRepository<Hospital, String> {
    @Query("""
    select h as hospital,
           (6371 * acos(cos(radians(:lat)) * cos(radians(COALESCE(h.lat,0)))
           * cos(radians(COALESCE(h.lon,0)) - radians(:lon))
           + sin(radians(:lat)) * sin(radians(COALESCE(h.lat,0))))) as distanceKm
    from Hospital h
    where h.lat is not null and h.lon is not null
    order by distanceKm asc
  """)
    List<Object[]> searchWithDistance(@Param("lat") double lat, @Param("lon") double lon);
}

