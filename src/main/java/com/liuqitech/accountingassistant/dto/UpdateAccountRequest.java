package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.AccountType;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 更新账户请求（全部字段可选，仅更新非 null 字段）。
 */
public class UpdateAccountRequest {

    @Size(max = 50, message = "账户名称不能超过50个字符")
    private String name;

    private AccountType type;

    private BigDecimal initialBalance;

    @Size(max = 50, message = "图标不能超过50个字符")
    private String icon;

    @Size(max = 20, message = "颜色代码不能超过20个字符")
    private String color;

    /** 是否在用；用 Boolean 以区分「未传」与「显式置为 false」 */
    private Boolean active;

    public UpdateAccountRequest() {}

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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
