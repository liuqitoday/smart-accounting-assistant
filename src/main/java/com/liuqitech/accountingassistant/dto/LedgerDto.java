package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.LedgerRole;

import java.time.LocalDateTime;

/**
 * 账本DTO（含当前用户在该账本中的角色与成员数）
 */
public class LedgerDto {

    private Long id;
    private String name;
    private String description;
    private String icon;
    private String color;
    private LedgerRole myRole;
    private long memberCount;
    private boolean owner;
    private boolean isDefault;
    private LocalDateTime createdAt;

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

    public LedgerRole getMyRole() {
        return myRole;
    }

    public void setMyRole(LedgerRole myRole) {
        this.myRole = myRole;
    }

    public long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(long memberCount) {
        this.memberCount = memberCount;
    }

    public boolean isOwner() {
        return owner;
    }

    public void setOwner(boolean owner) {
        this.owner = owner;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
