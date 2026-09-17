package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.exception.AccessDeniedException;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 账本访问控制服务：成员身份与角色校验的单一来源。
 */
@Service
@Transactional(readOnly = true)
public class LedgerAccessService {

    private final LedgerMemberRepository ledgerMemberRepository;

    public LedgerAccessService(LedgerMemberRepository ledgerMemberRepository) {
        this.ledgerMemberRepository = ledgerMemberRepository;
    }

    /**
     * 校验用户对账本拥有不低于 minRole 的权限，返回其成员行；否则抛 {@link AccessDeniedException}（→403）。
     */
    public LedgerMember requireAccess(String username, Long ledgerId, LedgerRole minRole) {
        LedgerMember member = resolveMembership(username, ledgerId);
        if (!member.getRole().atLeast(minRole)) {
            throw new AccessDeniedException("权限不足：该操作需要「" + roleLabel(minRole) + "」及以上权限");
        }
        return member;
    }

    /**
     * 取用户在账本中的成员身份；非成员抛 {@link AccessDeniedException}（→403）。供拦截器使用。
     */
    public LedgerMember resolveMembership(String username, Long ledgerId) {
        if (username == null || ledgerId == null) {
            throw new AccessDeniedException("无权访问该账本");
        }
        return ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, username)
                .orElseThrow(() -> new AccessDeniedException("无权访问该账本"));
    }

    /**
     * 查询成员身份（不抛异常）。
     */
    public Optional<LedgerMember> findMembership(String username, Long ledgerId) {
        if (username == null || ledgerId == null) {
            return Optional.empty();
        }
        return ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, username);
    }

    private String roleLabel(LedgerRole role) {
        switch (role) {
            case OWNER: return "所有者";
            case EDITOR: return "可编辑";
            default: return "仅查看";
        }
    }
}
