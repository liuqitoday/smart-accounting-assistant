package com.liuqitech.accountingassistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.SaveTransactionRequest;
import com.liuqitech.accountingassistant.dto.TransactionParseResponse;
import com.liuqitech.accountingassistant.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteException;
import org.sqlite.SQLiteErrorCode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Durable save receipts make retries safe, including after the original HTTP response was lost.
 * The first database statement claims a composite primary key. On SQLite this obtains the writer
 * lock before reading business data, avoiding a deferred read-to-write lock upgrade race.
 * The receipt, transaction, tags and response snapshot all commit in the same transaction.
 */
@Service
public class TransactionSaveService {
    private final TransactionService transactionService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public TransactionSaveService(TransactionService transactionService, JdbcTemplate jdbc,
                                  ObjectMapper objectMapper, PlatformTransactionManager manager) {
        this.transactionService = transactionService;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public TransactionParseResponse save(SaveTransactionRequest request, Long ledgerId, String username) {
        if (request.getClientRequestId() == null) {
            return transactionService.saveTransaction(request, ledgerId, username);
        }
        String requestId = normalizeRequestId(request.getClientRequestId());
        String hash = fingerprint(request);
        try {
            return transaction.execute(status -> {
                claim(ledgerId, username, requestId, hash);
                TransactionParseResponse response = transactionService.saveTransaction(request, ledgerId, username);
                jdbc.update("UPDATE transaction_save_receipts SET response_json = ? "
                                + "WHERE ledger_id = ? AND username = ? AND client_request_id = ?",
                        toJson(response), ledgerId, username, requestId);
                return response;
            });
        } catch (ReceiptAlreadyClaimed conflict) {
            // The failed transaction has rolled back before looking for the committed winner.
            // Do not turn unrelated persistence errors into successful idempotency responses.
            List<Receipt> receipts = jdbc.query("SELECT request_hash, response_json FROM transaction_save_receipts "
                            + "WHERE ledger_id = ? AND username = ? AND client_request_id = ?",
                    (rs, rowNum) -> new Receipt(rs.getString(1), rs.getString(2)), ledgerId, username, requestId);
            if (receipts.isEmpty()) throw conflict;
            Receipt receipt = receipts.get(0);
            if (!receipt.hash().equals(hash)) {
                throw new BusinessException("这次保存请求已用于另一笔内容，请重新发起保存", "IDEMPOTENCY_CONFLICT");
            }
            return fromJson(receipt.response());
        }
    }

    private void claim(Long ledgerId, String username, String requestId, String hash) {
        try {
            jdbc.update("INSERT INTO transaction_save_receipts "
                            + "(ledger_id, username, client_request_id, request_hash, response_json) "
                            + "VALUES (?, ?, ?, ?, '')", ledgerId, username, requestId, hash);
        } catch (DataAccessException error) {
            // Spring has no SQLite vendor error mapping, so its primary-key exception is
            // UncategorizedSQLException rather than DuplicateKeyException. Check the driver
            // result code explicitly and only recover a collision in this claim statement.
            boolean duplicate = error instanceof DuplicateKeyException;
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLiteException sqlite) {
                    duplicate |= sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY
                            || sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE;
                }
            }
            if (duplicate) throw new ReceiptAlreadyClaimed(error);
            throw error;
        }
    }

    private String normalizeRequestId(String value) {
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new BusinessException("请求标识必须为 UUID", "VALIDATION_ERROR");
        }
        return UUID.fromString(value).toString();
    }

    private String fingerprint(SaveTransactionRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("amount", decimal(request.getAmount()));
        values.put("type", request.getType());
        values.put("description", request.getDescription());
        values.put("originalText", request.getOriginalText());
        values.put("categoryId", request.getCategoryId());
        values.put("transactionDate", request.getTransactionDate());
        values.put("parsedMerchant", request.getParsedMerchant());
        values.put("confidenceScore", decimal(request.getConfidenceScore()));
        values.put("aiModelUsed", request.getAiModelUsed());
        values.put("note", request.getNote());
        values.put("relatedUser", request.getRelatedUser());
        values.put("aiSuggestedCategoryId", request.getAiSuggestedCategoryId());
        values.put("accountId", request.getAccountId());
        // Tags are a set: ordering and duplicates do not change the saved transaction.
        values.put("tagIds", request.getTagIds() == null ? List.of()
                : request.getTagIds().stream().distinct().sorted().toList());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(toJson(values).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize transaction save receipt", e);
        }
    }

    private TransactionParseResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, TransactionParseResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to read transaction save receipt", e);
        }
    }

    private record Receipt(String hash, String response) {}

    private static final class ReceiptAlreadyClaimed extends RuntimeException {
        private ReceiptAlreadyClaimed(Throwable cause) { super(cause); }
    }
}
