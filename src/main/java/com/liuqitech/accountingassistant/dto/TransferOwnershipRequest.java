package com.liuqitech.accountingassistant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 转让所有权请求
 */
public class TransferOwnershipRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
