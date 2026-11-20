package com.liuqitech.accountingassistant.mcp;

import java.util.List;

/** Created by authentication, never deserialized from tool arguments. */
public record McpCaller(String connectionId, String username, String tokenHash, boolean writable,
                        List<Long> ledgerIds, Long defaultLedgerId) {
    public static final String CONTEXT_KEY = McpCaller.class.getName();

    public List<String> scopes() {
        return writable
                ? List.of("reference:read", "transactions:read", "statistics:read", "transactions:create")
                : List.of("reference:read", "transactions:read", "statistics:read");
    }
}
