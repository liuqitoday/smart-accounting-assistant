package com.liuqitech.accountingassistant.util;

public final class SqlLike {

    public static final char ESCAPE = '\\';

    private SqlLike() {}

    public static String contains(String raw) {
        return "%" + escape(raw) + "%";
    }

    public static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
