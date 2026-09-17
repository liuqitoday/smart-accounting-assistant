package com.liuqitech.accountingassistant.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * 交易解析请求DTO
 */
public class TransactionParseRequest {

    @NotBlank(message = "交易文本不能为空")
    private String text;

    // 默认构造函数
    public TransactionParseRequest() {}

    // 构造函数
    public TransactionParseRequest(String text) {
        this.text = text;
    }

    // Getters and Setters
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "TransactionParseRequest{" +
                "text='" + text + '\'' +
                '}';
    }
}
