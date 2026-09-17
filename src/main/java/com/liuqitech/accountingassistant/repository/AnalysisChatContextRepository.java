package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.AnalysisChatContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalysisChatContextRepository extends JpaRepository<AnalysisChatContext, Long> {

    Optional<AnalysisChatContext> findByLedgerIdAndUserId(Long ledgerId, String userId);

    void deleteByLedgerIdAndUserId(Long ledgerId, String userId);

    void deleteByLedgerId(Long ledgerId);
}
