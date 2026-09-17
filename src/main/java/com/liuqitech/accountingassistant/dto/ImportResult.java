package com.liuqitech.accountingassistant.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV导入结果
 */
public class ImportResult {

    private int totalRows;
    private int createdCount;
    private int updatedCount;
    private int errorCount;
    private List<ImportError> errors = new ArrayList<>();

    public ImportResult() {}

    // Getters and Setters
    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public List<ImportError> getErrors() {
        return errors;
    }

    public void setErrors(List<ImportError> errors) {
        this.errors = errors;
    }

    /**
     * 导入错误详情
     */
    public static class ImportError {
        private int row;
        private String field;
        private String message;

        public ImportError() {}

        public ImportError(int row, String field, String message) {
            this.row = row;
            this.field = field;
            this.message = message;
        }

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
