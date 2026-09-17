package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.LedgerRole;

import java.time.LocalDateTime;

/**
 * 账本成员DTO
 */
public class LedgerMemberDto {

    private String username;
    private LedgerRole role;
    private LocalDateTime joinedAt;

    public LedgerMemberDto() {}

    public LedgerMemberDto(String username, LedgerRole role, LocalDateTime joinedAt) {
        this.username = username;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LedgerRole getRole() {
        return role;
    }

    public void setRole(LedgerRole role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
