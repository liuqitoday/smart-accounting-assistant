package com.liuqitech.accountingassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建标签请求
 */
public class CreateTagRequest {

    @NotBlank(message = "标签名称不能为空")
    @Size(max = 50, message = "标签名称不能超过50个字符")
    private String name;

    @Size(max = 20, message = "颜色代码不能超过20个字符")
    private String color;

    public CreateTagRequest() {}

    public CreateTagRequest(String name, String color) {
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
