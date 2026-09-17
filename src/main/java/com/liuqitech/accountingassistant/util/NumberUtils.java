package com.liuqitech.accountingassistant.util;

import java.math.BigDecimal;

/**
 * 数值转换工具 — 统一处理数据库聚合查询返回的 Object → 数值类型转换。
 */
public final class NumberUtils {

    private NumberUtils() {}

    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    public static Long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Long l) return l;
        return Long.valueOf(value.toString());
    }
}
