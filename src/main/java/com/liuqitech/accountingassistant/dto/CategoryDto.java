package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.TransactionType;

import java.util.List;

/**
 * 分类DTO
 */
public class CategoryDto {
    
    private Long id;
    private String name;
    private Long parentId;
    private String parentName;
    private Integer level;
    private TransactionType type;
    private String description;
    private List<CategoryDto> children;
    
    // AI分类解析结果相关字段
    private Double confidence;
    private String reason;
    
    // 默认构造函数
    public CategoryDto() {}
    
    // 构造函数
    public CategoryDto(Long id, String name, Long parentId, String parentName, 
                      Integer level, TransactionType type, String description) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.parentName = parentName;
        this.level = level;
        this.type = type;
        this.description = description;
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
    
    public Long getParentId() {
        return parentId;
    }
    
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
    
    public String getParentName() {
        return parentName;
    }
    
    public void setParentName(String parentName) {
        this.parentName = parentName;
    }
    
    public Integer getLevel() {
        return level;
    }
    
    public void setLevel(Integer level) {
        this.level = level;
    }
    
    public TransactionType getType() {
        return type;
    }
    
    public void setType(TransactionType type) {
        this.type = type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<CategoryDto> getChildren() {
        return children;
    }
    
    public void setChildren(List<CategoryDto> children) {
        this.children = children;
    }
    
    public Double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    @Override
    public String toString() {
        return "CategoryDto{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", parentName='" + parentName + '\'' +
                ", level=" + level +
                ", type=" + type +
                ", description='" + description + '\'' +
                ", confidence=" + confidence +
                ", reason='" + reason + '\'' +
                '}';
    }
}
