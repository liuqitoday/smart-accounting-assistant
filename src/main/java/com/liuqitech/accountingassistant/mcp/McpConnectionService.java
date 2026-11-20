package com.liuqitech.accountingassistant.mcp;

import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.exception.AccessDeniedException;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import com.liuqitech.accountingassistant.service.LedgerAccessService;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class McpConnectionService {
    private final JdbcTemplate jdbc;
    private final LedgerAccessService access;
    private final SecureRandom random = new SecureRandom();

    public McpConnectionService(JdbcTemplate jdbc, LedgerAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public record CreateRequest(@NotBlank @Size(max = 80) String name,
                                @NotEmpty @Size(max = 50) List<@NotNull @Positive Long> ledgerIds,
                                @Positive Long defaultLedgerId, boolean writable,
                                @Min(1) @Max(365) int expiresInDays) {}

    public record Connection(String id, String name, String tokenPrefix, List<String> scopes,
                             List<Long> ledgerIds, Long defaultLedgerId, boolean writable,
                             Instant createdAt, Instant expiresAt, Instant lastUsedAt, String status) {}
    public record Issued(Connection connection, String token) {}
    public record CallLog(String id, String toolName, Long ledgerId, String requestId,
                          Long transactionId, String outcome, String errorCode,
                          Instant createdAt, long durationMs) {}

    @Transactional(readOnly = true)
    public List<Connection> list(String username) {
        return jdbc.query("SELECT * FROM mcp_connections WHERE username = ? ORDER BY created_at DESC",
                (rs, row) -> view(rs), username);
    }

    @Transactional
    public Issued create(String username, CreateRequest request) {
        // First statement takes the SQLite writer lock before reading membership.
        String id = UUID.randomUUID().toString();
        String token = newToken();
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO mcp_connections "
                        + "(id, username, name, token_hash, token_prefix, writable, default_ledger_id, created_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, username, request.name().trim(), digest(token), token.substring(0, 15),
                request.writable(), request.defaultLedgerId(), now,
                Instant.ofEpochMilli(now).plus(request.expiresInDays(), ChronoUnit.DAYS).toEpochMilli());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM mcp_connections WHERE username = ? AND revoked_at IS NULL AND expires_at > ?",
                Integer.class, username, now) > 50) {
            throw new BusinessException("最多同时启用 50 个 MCP 连接", "CONNECTION_LIMIT");
        }
        Set<Long> ledgerIds = new LinkedHashSet<>(request.ledgerIds());
        if (request.defaultLedgerId() != null && !ledgerIds.contains(request.defaultLedgerId())) {
            throw new BusinessException("默认账本必须在授权范围内", "VALIDATION_ERROR");
        }
        for (Long ledgerId : ledgerIds) {
            access.requireAccess(username, ledgerId, LedgerRole.VIEWER);
            jdbc.update("INSERT INTO mcp_connection_ledgers (connection_id, ledger_id) VALUES (?, ?)", id, ledgerId);
        }
        return new Issued(owned(username, id), token);
    }

    @Transactional
    public void revoke(String username, String id) {
        int changed = jdbc.update("UPDATE mcp_connections SET revoked_at = COALESCE(revoked_at, ?) WHERE id = ? AND username = ?",
                System.currentTimeMillis(), id, username);
        if (changed == 0) throw new ResourceNotFoundException("连接不存在");
    }

    @Transactional
    public Issued rotate(String username, String id) {
        String token = newToken();
        int changed = jdbc.update("UPDATE mcp_connections SET token_hash = ?, token_prefix = ? "
                        + "WHERE id = ? AND username = ? AND revoked_at IS NULL AND expires_at > ?",
                digest(token), token.substring(0, 15), id, username, System.currentTimeMillis());
        if (changed == 0) throw new BusinessException("连接已失效，请创建新的连接", "CONNECTION_INACTIVE");
        return new Issued(owned(username, id), token);
    }

    @Transactional
    public void revokeAll(String username) {
        jdbc.update("UPDATE mcp_connections SET revoked_at = ? WHERE username = ? AND revoked_at IS NULL",
                System.currentTimeMillis(), username);
    }

    @Transactional(readOnly = true)
    public Optional<McpCaller> authenticate(String token) {
        if (token == null || !token.matches("aa_mcp_[A-Za-z0-9_-]{43}")) return Optional.empty();
        return byHash(digest(token));
    }

    @Transactional(readOnly = true)
    public McpCaller refresh(McpCaller caller) {
        if (caller == null) throw new AccessDeniedException("MCP 连接未授权");
        return byHash(caller.tokenHash()).filter(c -> c.connectionId().equals(caller.connectionId()))
                .orElseThrow(() -> new AccessDeniedException("MCP 连接已失效，请重新连接"));
    }

    private Optional<McpCaller> byHash(String hash) {
        return jdbc.query("SELECT c.* FROM mcp_connections c JOIN users u ON u.username = c.username "
                        + "WHERE c.token_hash = ? AND c.revoked_at IS NULL AND c.expires_at > ?",
                (rs, row) -> new McpCaller(rs.getString("id"), rs.getString("username"), hash,
                        rs.getBoolean("writable"), ledgerIds(rs.getString("id")),
                        rs.getObject("default_ledger_id") == null ? null : rs.getLong("default_ledger_id")),
                hash, System.currentTimeMillis()).stream().findFirst();
    }

    public void authorize(McpCaller caller, Long ledgerId, boolean write) {
        if (ledgerId == null || !caller.ledgerIds().contains(ledgerId) || (write && !caller.writable())) {
            throw new AccessDeniedException("连接没有该账本或操作的权限");
        }
        access.requireAccess(caller.username(), ledgerId, write ? LedgerRole.EDITOR : LedgerRole.VIEWER);
    }

    @Transactional
    public void record(McpCaller caller, String tool, Long ledgerId, String requestId,
                       Long transactionId, String outcome, String errorCode, long durationMs) {
        jdbc.update("INSERT INTO mcp_call_logs "
                        + "(id, connection_id, ledger_id, tool_name, request_id, transaction_id, outcome, error_code, created_at, duration_ms) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), caller.connectionId(), ledgerId, tool, requestId,
                transactionId, outcome, errorCode, System.currentTimeMillis(), durationMs);
        jdbc.update("UPDATE mcp_connections SET last_used_at = ? WHERE id = ?", System.currentTimeMillis(), caller.connectionId());
    }

    @Transactional(readOnly = true)
    public List<CallLog> calls(String username, String id) {
        owned(username, id);
        return jdbc.query("SELECT * FROM mcp_call_logs WHERE connection_id = ? ORDER BY created_at DESC, id DESC LIMIT 100",
                (rs, row) -> new CallLog(rs.getString("id"), rs.getString("tool_name"),
                        rs.getObject("ledger_id") == null ? null : rs.getLong("ledger_id"), rs.getString("request_id"),
                        rs.getObject("transaction_id") == null ? null : rs.getLong("transaction_id"),
                        rs.getString("outcome"), rs.getString("error_code"), Instant.ofEpochMilli(rs.getLong("created_at")),
                        rs.getLong("duration_ms")), id);
    }

    @Scheduled(cron = "${app.mcp.cleanup-cron:0 30 3 * * *}", zone = "Asia/Shanghai")
    @Transactional
    public void cleanLogs() {
        jdbc.update("DELETE FROM mcp_call_logs WHERE outcome <> 'CREATED' AND created_at < ?",
                Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli());
    }

    private Connection owned(String username, String id) {
        return jdbc.query("SELECT * FROM mcp_connections WHERE id = ? AND username = ?",
                (rs, row) -> view(rs), id, username).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("连接不存在"));
    }

    private Connection view(java.sql.ResultSet rs) throws java.sql.SQLException {
        String id = rs.getString("id");
        boolean writable = rs.getBoolean("writable");
        long expires = rs.getLong("expires_at");
        return new Connection(id, rs.getString("name"), rs.getString("token_prefix"),
                new McpCaller(id, "", "", writable, List.of(), null).scopes(), ledgerIds(id),
                rs.getObject("default_ledger_id") == null ? null : rs.getLong("default_ledger_id"), writable,
                Instant.ofEpochMilli(rs.getLong("created_at")), Instant.ofEpochMilli(expires),
                rs.getObject("last_used_at") == null ? null : Instant.ofEpochMilli(rs.getLong("last_used_at")),
                rs.getObject("revoked_at") != null ? "REVOKED" : expires <= System.currentTimeMillis() ? "EXPIRED" : "ACTIVE");
    }

    private List<Long> ledgerIds(String id) {
        return jdbc.queryForList("SELECT ledger_id FROM mcp_connection_ledgers WHERE connection_id = ? ORDER BY ledger_id", Long.class, id);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "aa_mcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
