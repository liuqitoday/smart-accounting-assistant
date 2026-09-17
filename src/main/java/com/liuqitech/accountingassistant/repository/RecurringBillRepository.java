package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.RecurringBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringBillRepository extends JpaRepository<RecurringBill, Long> {

    List<RecurringBill> findByLedgerIdAndDeletedAtIsNullOrderByIdDesc(Long ledgerId);

    Optional<RecurringBill> findByIdAndLedgerIdAndDeletedAtIsNull(Long id, Long ledgerId);

    List<RecurringBill> findByEnabledTrueAndDeletedAtIsNullAndNextRunDateLessThanEqual(LocalDate date);

    List<RecurringBill> findByLedgerIdAndEnabledTrueAndDeletedAtIsNullAndNextRunDateLessThanEqual(Long ledgerId, LocalDate date);

    void deleteByLedgerId(Long ledgerId);
}
