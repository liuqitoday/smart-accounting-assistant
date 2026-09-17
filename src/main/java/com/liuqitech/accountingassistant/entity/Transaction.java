package com.liuqitech.accountingassistant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.util.LocalDateStringConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 交易记录实体类
 */
@Entity
@Table(name = "transactions")
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "original_text", nullable = false, columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "category", length = 100)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "children", "parent"})
    @JsonIgnore
    private Category categoryEntity;

    @Column(name = "parent_category_id")
    private Long parentCategoryId;

    @Column(name = "parent_category_name", length = 100)
    private String parentCategoryName;

    @Column(name = "transaction_date", nullable = false)
    @Convert(converter = LocalDateStringConverter.class)
    private LocalDate transactionDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "related_user", length = 100)
    private String relatedUser;
    
    @Column(name = "parsed_merchant")
    private String parsedMerchant;
    
    @Column(name = "parsed_amount", precision = 15, scale = 2)
    private BigDecimal parsedAmount;
    
    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;
    
    @Column(name = "ai_model_used")
    private String aiModelUsed;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    /** 所属账本（数据隔离维度）。迁移后回填，应用层保证非空。 */
    @Column(name = "ledger_id")
    private Long ledgerId;

    /** 关联账户（可选）。收入使该账户余额 +，支出 −；为空表示「未指定账户」。
     *  当 type=TRANSFER 时表示转出账户。 */
    @Column(name = "account_id")
    private Long accountId;

    /** 转入账户，仅在 type=TRANSFER 时使用：使该账户余额 +。其余类型为空。 */
    @Column(name = "counter_account_id")
    private Long counterAccountId;

    /** 周期账单来源规则；为空表示普通手动/导入/AI 交易。 */
    @Column(name = "recurring_bill_id")
    private Long recurringBillId;

    /** 周期账单的所属期，用于数据库唯一约束防止重复生成。 */
    @Column(name = "recurring_occurrence_date")
    @Convert(converter = LocalDateStringConverter.class)
    private LocalDate recurringOccurrenceDate;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "transaction_tags",
        joinColumns = @JoinColumn(name = "transaction_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @JsonIgnoreProperties({"createdBy", "createdAt", "updatedAt", "hibernateLazyInitializer", "handler"})
    private Set<Tag> tags = new HashSet<>();

    // 默认构造函数
    public Transaction() {}
    
    // 构造函数
    public Transaction(BigDecimal amount, TransactionType type, String description, 
                      String originalText, Category categoryEntity, LocalDate transactionDate) {
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.originalText = originalText;
        this.transactionDate = transactionDate;
        setCategoryEntity(categoryEntity);
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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
    
    public String getOriginalText() {
        return originalText;
    }
    
    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }
    
    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Category getCategoryEntity() {
        return categoryEntity;
    }

    public void setCategoryEntity(Category categoryEntity) {
        this.categoryEntity = categoryEntity;
        if (categoryEntity == null) {
            this.categoryId = null;
            this.category = null;
            this.parentCategoryId = null;
            this.parentCategoryName = null;
            return;
        }

        this.categoryId = categoryEntity.getId();
        this.category = categoryEntity.getName();

        if (categoryEntity.getParent() != null) {
            this.parentCategoryId = categoryEntity.getParent().getId();
            this.parentCategoryName = categoryEntity.getParent().getName();
        } else {
            this.parentCategoryId = null;
            this.parentCategoryName = null;
        }
    }
    
    public LocalDate getTransactionDate() {
        return transactionDate;
    }
    
    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public Long getParentCategoryId() {
        return parentCategoryId;
    }

    public void setParentCategoryId(Long parentCategoryId) {
        this.parentCategoryId = parentCategoryId;
    }

    public String getParentCategoryName() {
        return parentCategoryName;
    }

    public void setParentCategoryName(String parentCategoryName) {
        this.parentCategoryName = parentCategoryName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getRelatedUser() {
        return relatedUser;
    }

    public void setRelatedUser(String relatedUser) {
        this.relatedUser = relatedUser;
    }

    public String getParsedMerchant() {
        return parsedMerchant;
    }
    
    public void setParsedMerchant(String parsedMerchant) {
        this.parsedMerchant = parsedMerchant;
    }
    
    public BigDecimal getParsedAmount() {
        return parsedAmount;
    }
    
    public void setParsedAmount(BigDecimal parsedAmount) {
        this.parsedAmount = parsedAmount;
    }
    
    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }
    
    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
    
    public String getAiModelUsed() {
        return aiModelUsed;
    }
    
    public void setAiModelUsed(String aiModelUsed) {
        this.aiModelUsed = aiModelUsed;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Long getLedgerId() {
        return ledgerId;
    }

    public void setLedgerId(Long ledgerId) {
        this.ledgerId = ledgerId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getCounterAccountId() {
        return counterAccountId;
    }

    public void setCounterAccountId(Long counterAccountId) {
        this.counterAccountId = counterAccountId;
    }

    public Long getRecurringBillId() {
        return recurringBillId;
    }

    public void setRecurringBillId(Long recurringBillId) {
        this.recurringBillId = recurringBillId;
    }

    public LocalDate getRecurringOccurrenceDate() {
        return recurringOccurrenceDate;
    }

    public void setRecurringOccurrenceDate(LocalDate recurringOccurrenceDate) {
        this.recurringOccurrenceDate = recurringOccurrenceDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", amount=" + amount +
                ", type=" + type +
                ", description='" + description + '\'' +
                ", transactionDate=" + transactionDate +
                ", parsedMerchant='" + parsedMerchant + '\'' +
                ", confidenceScore=" + confidenceScore +
                '}';
    }
}
