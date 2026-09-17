package com.liuqitech.accountingassistant.dto;

/**
 * AI 分类解析结果（结构化输出目标类型）
 */
public class CategoryParseResult {

    private Long categoryId;
    private String categoryName;
    private Long parentCategoryId;
    private String parentCategoryName;
    private Double confidence;
    private String reason;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Long getParentCategoryId() { return parentCategoryId; }
    public void setParentCategoryId(Long parentCategoryId) { this.parentCategoryId = parentCategoryId; }
    public String getParentCategoryName() { return parentCategoryName; }
    public void setParentCategoryName(String parentCategoryName) { this.parentCategoryName = parentCategoryName; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    @Override
    public String toString() {
        return "CategoryParseResult{" +
                "categoryId=" + categoryId +
                ", categoryName='" + categoryName + '\'' +
                ", parentCategoryId=" + parentCategoryId +
                ", confidence=" + confidence +
                '}';
    }
}
