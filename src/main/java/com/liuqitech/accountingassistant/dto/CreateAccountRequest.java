package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建账户请求
 */
public class CreateAccountRequest {

    @NotBlank(message = "账户名称不能为空")
    @Size(max = 50, message = "账户名称不能超过50个字符")
    private String name;

    @NotNull(message = "账户类型不能为空")
    private AccountType type;

    /** 期初余额，可空（默认 0） */
    private BigDecimal initialBalance;

    @Size(max = 50, message = "图标不能超过50个字符")
    private String icon;

    @Size(max = 20, message = "颜色代码不能超过20个字符")
    private String color;

    public CreateAccountRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
