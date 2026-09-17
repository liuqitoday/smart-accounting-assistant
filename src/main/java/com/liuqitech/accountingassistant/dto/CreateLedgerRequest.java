package com.liuqitech.accountingassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建账本请求
 */
public class CreateLedgerRequest {

    @NotBlank(message = "账本名称不能为空")
    @Size(max = 100, message = "账本名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    @Size(max = 50)
    private String icon;

    @Size(max = 20)
    private String color;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
