package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    /** 默认账本被删除时，用于重新指向其它账本 */
    List<User> findByDefaultLedgerId(Long defaultLedgerId);

}
