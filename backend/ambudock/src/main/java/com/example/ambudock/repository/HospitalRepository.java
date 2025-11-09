package com.example.ambudock.repository;

import com.example.ambudock.domain.hospital.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


/**
 * Hospital JPA Repository
 *
 * - PK 타입이 String인 Hospital 엔티티용 기본 CRUD 제공(JpaRepository<Hospital, String>).
 * - 사용 목적: (lat, lon)이 있는 병원들을 기준 좌표로부터의 거리(km) 오름차순으로 조회.
 *   * 맵에 초기 마커 뿌리기/반경 후보 조회 같은 1차 필터에 사용.
 *   * AmbuDock 규칙상 Top3 카드의 최종 거리/ETA는 TMAP Proxy에서 가져오고,
 *     이 쿼리는 '초기 표시용' 직선거리 계산에만 사용.
 */
public interface HospitalRepository extends JpaRepository<Hospital, String> {

    //기준 좌표(:lat, :lon)로부터 각 병원까지의 대원거리를 km 단위로 계산하여 (병원, 거리) 튜플을 거리 오름차순으로 반환한다.
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

