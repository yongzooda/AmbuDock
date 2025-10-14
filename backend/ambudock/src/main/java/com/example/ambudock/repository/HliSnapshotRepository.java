package com.example.ambudock.repository;

import com.example.ambudock.domain.hospital.HliSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HliSnapshotRepository extends JpaRepository<HliSnapshot, Long> {
    Optional<HliSnapshot> findByStatus_StatusId(Long statusId);
}

