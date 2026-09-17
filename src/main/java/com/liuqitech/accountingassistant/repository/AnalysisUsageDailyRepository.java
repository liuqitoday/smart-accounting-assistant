package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.AnalysisUsageDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AnalysisUsageDailyRepository extends JpaRepository<AnalysisUsageDaily, Long> {

    Optional<AnalysisUsageDaily> findByUserIdAndBusinessDate(String userId, LocalDate businessDate);
}
