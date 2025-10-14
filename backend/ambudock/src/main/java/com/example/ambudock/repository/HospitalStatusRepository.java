package com.example.ambudock.repository;

import com.example.ambudock.domain.hospital.HospitalStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HospitalStatusRepository extends JpaRepository<HospitalStatus, Long> {
    @Query("""
    select hs from HospitalStatus hs
    where hs.hospital.hpid = :hpid
    order by hs.hvidate desc
  """)
    List<HospitalStatus> findLatestByHpid(@Param("hpid") String hpid, Pageable pageable);
}

