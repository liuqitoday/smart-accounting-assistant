package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.LedgerRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 邀请成员请求：按用户名直接添加，并指定角色（EDITOR / VIEWER）
 */
public class InviteMemberRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotNull(message = "角色不能为空")
    private LedgerRole role;

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
}
