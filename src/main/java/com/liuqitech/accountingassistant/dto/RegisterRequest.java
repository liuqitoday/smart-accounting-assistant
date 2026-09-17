package com.liuqitech.accountingassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    // 公网部署密码策略：至少 10 位且同时含字母和数字（仅约束新注册，存量用户登录不受影响）；
    // max 64 防超长输入（BCrypt 72 字节截断）
    @NotBlank(message = "密码不能为空")
    @Size(min = 10, max = 64, message = "密码长度需为 10-64 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "密码需同时包含字母和数字")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
