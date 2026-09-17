package com.liuqitech.accountingassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;

    // 与注册一致的密码策略（仅约束新密码，存量密码登录/验证不受影响）
    @NotBlank(message = "新密码不能为空")
    @Size(min = 10, max = 64, message = "新密码长度需为 10-64 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "新密码需同时包含字母和数字")
    private String newPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
