package com.liuqitech.accountingassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liuqitech.accountingassistant.enums.AccountType;

/** Lightweight input options; deliberately avoids computing account balances. */
public record AccountOptionDto(Long id, String name, AccountType type, String icon, String color,
                               boolean active, @JsonProperty("default") boolean isDefault) {}
