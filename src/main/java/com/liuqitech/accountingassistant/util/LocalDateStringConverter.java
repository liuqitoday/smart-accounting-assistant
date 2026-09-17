package com.liuqitech.accountingassistant.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

/**
 * SQLite 将日期存储为 TEXT，JDBC 驱动的 getDate() 无法直接解析 yyyy-MM-dd 格式，
 * 因此需要通过 AttributeConverter 手动处理 LocalDate 与 String 的转换。
 */
@Converter
public class LocalDateStringConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        return (dbData == null || dbData.isBlank()) ? null : LocalDate.parse(dbData.substring(0, 10));
    }
}
