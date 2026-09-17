package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.AnalysisUsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;

@Repository
public interface AnalysisUsageEventRepository extends JpaRepository<AnalysisUsageEvent, Long> {

    long countByUserId(String userId);

    long countByUserIdAndCreatedAtGreaterThanEqual(String userId, LocalDateTime since);

    long countByUserIdAndCreatedAtGreaterThanEqualAndStatusIn(
            String userId, LocalDateTime since, Collection<String> statuses);
}
