package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.LedgerRole;
import jakarta.validation.constraints.NotNull;

/**
 * 修改成员角色请求（EDITOR / VIEWER；转让所有权请用 transfer 接口）
 */
public class UpdateMemberRoleRequest {

    @NotNull(message = "角色不能为空")
    private LedgerRole role;

    public LedgerRole getRole() {
        return role;
    }

    public void setRole(LedgerRole role) {
        this.role = role;
    }
}
