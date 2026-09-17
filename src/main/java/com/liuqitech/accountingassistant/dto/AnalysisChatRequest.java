package com.liuqitech.accountingassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AnalysisChatRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过500字符")
    private String question;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
