package com.liuqitech.accountingassistant.dto;

import java.time.LocalDateTime;

/**
 * 标签DTO
 */
public class TagDto {

    private Long id;
    private String name;
    private String color;
    private boolean system;
    private LocalDateTime createdAt;

    public TagDto() {}

    public TagDto(Long id, String name, String color, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
