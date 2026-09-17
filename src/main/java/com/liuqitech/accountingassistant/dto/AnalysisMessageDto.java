package com.liuqitech.accountingassistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisMessageDto {
    private Long id;
    private String role;  // USER | ASSISTANT
    private String content;
    private String payload;  // JSON string
    private String status;   // OK | FAILED
    private LocalDateTime createdAt;

    public AnalysisMessageDto() {}

    public AnalysisMessageDto(Long id, String role, String content, String payload, String status, LocalDateTime createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
