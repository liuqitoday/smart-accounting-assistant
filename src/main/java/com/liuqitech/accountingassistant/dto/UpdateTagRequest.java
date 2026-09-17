package com.liuqitech.accountingassistant.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新标签请求
 */
public class UpdateTagRequest {

    @Size(max = 50, message = "标签名称不能超过50个字符")
    private String name;

    @Size(max = 20, message = "颜色代码不能超过20个字符")
    private String color;

    public UpdateTagRequest() {}

    public UpdateTagRequest(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
