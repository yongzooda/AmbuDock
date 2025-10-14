package com.example.ambudock.repository;

import com.example.ambudock.domain.disaster.DisasterAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DisasterAlertRepository extends JpaRepository<DisasterAlert, Long> {
    @Query("select a from DisasterAlert a where a.isActive = true and a.severity <> 'IGNORE' order by a.issuedAt desc")
    List<DisasterAlert> findActiveAlerts();
}

