package com.liuqitech.accountingassistant.dto;

import java.util.ArrayList;
import java.util.List;

public class GenerateRecurringBillsResult {

    private int processedRuleCount;
    private int generatedCount;
    private int skippedCount;
    private int truncatedRuleCount;
    private boolean truncated;
    private final List<Long> generatedTransactionIds = new ArrayList<>();

    public int getProcessedRuleCount() {
        return processedRuleCount;
    }

    public void setProcessedRuleCount(int processedRuleCount) {
        this.processedRuleCount = processedRuleCount;
    }

    public int getGeneratedCount() {
        return generatedCount;
    }

    public void setGeneratedCount(int generatedCount) {
        this.generatedCount = generatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getTruncatedRuleCount() {
        return truncatedRuleCount;
    }

    public void setTruncatedRuleCount(int truncatedRuleCount) {
        this.truncatedRuleCount = truncatedRuleCount;
        this.truncated = truncatedRuleCount > 0;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public List<Long> getGeneratedTransactionIds() {
        return generatedTransactionIds;
    }

    public void addProcessedRule() {
        processedRuleCount++;
    }

    public void addGenerated(Long transactionId) {
        generatedCount++;
        generatedTransactionIds.add(transactionId);
    }

    public void addSkipped() {
        skippedCount++;
    }

    public void addTruncatedRule() {
        truncatedRuleCount++;
        truncated = true;
    }

    public void merge(GenerateRecurringBillsResult other) {
        this.processedRuleCount += other.processedRuleCount;
        this.generatedCount += other.generatedCount;
        this.skippedCount += other.skippedCount;
        this.truncatedRuleCount += other.truncatedRuleCount;
        this.truncated = this.truncated || other.truncated;
        this.generatedTransactionIds.addAll(other.generatedTransactionIds);
    }
}
