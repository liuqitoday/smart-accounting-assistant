package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 账本数据访问接口
 */
@Repository
public interface LedgerRepository extends JpaRepository<Ledger, Long> {

    /** 某用户作为所有者创建的账本 */
    List<Ledger> findByOwnerUsername(String ownerUsername);
}
