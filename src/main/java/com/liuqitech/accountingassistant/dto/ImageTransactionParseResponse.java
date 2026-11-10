package com.liuqitech.accountingassistant.dto;

import java.util.List;

/** Image recognition drafts. The caller must review and save them explicitly. */
public record ImageTransactionParseResponse(List<TransactionParseResponse> transactions,
                                            List<String> warnings) {
}
