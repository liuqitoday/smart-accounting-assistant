package com.liuqitech.accountingassistant.dto;

import java.util.List;

/** Public feature capabilities; never includes provider credentials or configuration. */
public record TransactionCapabilities(boolean imageParsingEnabled,
                                      long maxImageSizeBytes,
                                      List<String> supportedImageTypes,
                                      int maxTransactionsPerImage) {
}
