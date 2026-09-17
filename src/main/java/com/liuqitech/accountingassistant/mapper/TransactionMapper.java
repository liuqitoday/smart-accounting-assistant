package com.liuqitech.accountingassistant.mapper;

import com.liuqitech.accountingassistant.dto.TagDto;
import com.liuqitech.accountingassistant.dto.TransactionDto;
import com.liuqitech.accountingassistant.dto.TransactionParseResponse;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.service.TagService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps transaction persistence objects to stable API DTOs.
 */
@Component
public class TransactionMapper {

    private final TagService tagService;

    public TransactionMapper(TagService tagService) {
        this.tagService = tagService;
    }

    public TransactionDto toDto(Transaction transaction) {
        TransactionDto dto = new TransactionDto();
        dto.setId(transaction.getId());
        dto.setAmount(transaction.getAmount());
        dto.setType(transaction.getType());
        dto.setDescription(transaction.getDescription());
        dto.setOriginalText(transaction.getOriginalText());
        dto.setCategoryId(transaction.getCategoryId());
        dto.setCategory(transaction.getCategory());
        dto.setCategoryName(transaction.getCategory());
        dto.setParentCategoryId(transaction.getParentCategoryId());
        dto.setParentCategoryName(transaction.getParentCategoryName());
        dto.setTransactionDate(transaction.getTransactionDate());
        dto.setNote(transaction.getNote());
        dto.setRelatedUser(transaction.getRelatedUser());
        dto.setParsedMerchant(transaction.getParsedMerchant());
        dto.setParsedAmount(transaction.getParsedAmount());
        dto.setConfidenceScore(transaction.getConfidenceScore());
        dto.setAiModelUsed(transaction.getAiModelUsed());
        dto.setCreatedBy(transaction.getCreatedBy());
        dto.setAccountId(transaction.getAccountId());
        dto.setCounterAccountId(transaction.getCounterAccountId());
        dto.setRecurringBillId(transaction.getRecurringBillId());
        dto.setRecurringOccurrenceDate(transaction.getRecurringOccurrenceDate());
        dto.setCreatedAt(transaction.getCreatedAt());
        dto.setUpdatedAt(transaction.getUpdatedAt());
        dto.setTags(toTagDtos(transaction));
        return dto;
    }

    public TransactionParseResponse toParseResponse(Transaction transaction) {
        TransactionParseResponse response = new TransactionParseResponse();
        response.setId(transaction.getId());
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setDescription(transaction.getDescription());
        response.setOriginalText(transaction.getOriginalText());
        response.setCategoryId(transaction.getCategoryId());
        response.setCategoryName(transaction.getCategory());
        response.setParentCategoryId(transaction.getParentCategoryId());
        response.setParentCategoryName(transaction.getParentCategoryName());
        response.setTransactionDate(transaction.getTransactionDate());
        response.setParsedMerchant(transaction.getParsedMerchant());
        response.setConfidenceScore(transaction.getConfidenceScore());
        response.setAiModelUsed(transaction.getAiModelUsed());
        response.setNote(transaction.getNote());
        response.setRelatedUser(transaction.getRelatedUser());
        response.setAccountId(transaction.getAccountId());
        response.setTags(toTagDtos(transaction));
        return response;
    }

    private List<TagDto> toTagDtos(Transaction transaction) {
        return tagService.convertToDtoList(transaction.getTags());
    }
}
