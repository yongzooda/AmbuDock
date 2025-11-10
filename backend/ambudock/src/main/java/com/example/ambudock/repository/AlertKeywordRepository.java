package com.example.ambudock.repository;

import com.example.ambudock.domain.disaster.AlertKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertKeywordRepository extends JpaRepository<AlertKeyword, Long> {
    List<AlertKeyword> findByEnabledTrueOrderByPriorityAscRuleIdAsc();
}
