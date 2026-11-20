package com.liuqitech.accountingassistant.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.liuqitech.accountingassistant.exception.AccessDeniedException;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerConfig {
    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

    @Bean
    WebMvcStatelessServerTransport accountingMcpTransport(@Qualifier("mcpServerObjectMapper") ObjectMapper mapper) {
        return WebMvcStatelessServerTransport.builder().jsonMapper(new JacksonMcpJsonMapper(mapper))
                .messageEndpoint("/mcp")
                .contextExtractor(request -> McpTransportContext.create(Map.of(McpCaller.CONTEXT_KEY,
                        Objects.requireNonNull(request.servletRequest().getAttribute(McpCaller.CONTEXT_KEY)))))
                .build();
    }

    @Bean
    List<McpStatelessServerFeatures.SyncToolSpecification> accountingTools(McpAccountingFacade facade,
            McpConnectionService connections, ObjectMapper mapper) {
        ObjectMapper output = mapper.copy().registerModule(new SimpleModule().addSerializer(BigDecimal.class, ToStringSerializer.instance));
        return McpToolDefinitions.all().stream().map(definition -> new McpStatelessServerFeatures.SyncToolSpecification(
                definition.tool(), (context, request) -> call(definition, context, request, facade, connections, output))).toList();
    }

    private McpSchema.CallToolResult call(McpToolDefinitions.Definition definition, McpTransportContext context,
            McpSchema.CallToolRequest request, McpAccountingFacade facade, McpConnectionService connections, ObjectMapper mapper) {
        long start = System.nanoTime();
        McpCaller caller = null;
        Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
        String error = null;
        Long transactionId = null;
        String requestId = null;
        try {
            caller = connections.refresh((McpCaller) context.get(McpCaller.CONTEXT_KEY));
            definition.validate(args);
            requestId = (String) args.get("clientRequestId");
            Object data = facade.execute(definition.name(), caller, args);
            if (definition.name().equals("accounting_create_transaction")) {
                transactionId = ((Number) ((Map<?, ?>) data).get("transactionId")).longValue();
            }
            String json = mapper.writeValueAsString(Map.of("ok", true, "data", data));
            if (json.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
                throw new BusinessException("结果超过 1 MiB，请缩小查询范围或每页数量", "RESULT_TOO_LARGE");
            }
            return McpSchema.CallToolResult.builder().structuredContent(mapper.readTree(json)).addTextContent(json).isError(false).build();
        } catch (Exception e) {
            String message;
            boolean retryable = false;
            if (e instanceof AccessDeniedException) { error = "FORBIDDEN"; message = e.getMessage(); }
            else if (e instanceof ResourceNotFoundException) { error = "NOT_FOUND"; message = "记录或选项不存在于当前账本"; }
            else if (e instanceof BusinessException b) { error = b.getErrorCode() == null ? "VALIDATION_ERROR" : b.getErrorCode(); message = b.getMessage(); }
            else { error = "INTERNAL_ERROR"; message = "服务暂时不可用；写入请用完全相同的参数及 clientRequestId 重试"; retryable = true; log.error("MCP tool {} failed ({})", definition.name(), e.getClass().getSimpleName()); }
            var body = Map.of("ok", false, "errorCode", error, "message", message, "retryable", retryable);
            try {
                return McpSchema.CallToolResult.builder().structuredContent(body).addTextContent(mapper.writeValueAsString(body)).isError(true).build();
            } catch (Exception impossible) { throw new IllegalStateException(impossible); }
        } finally {
            if (caller != null) {
                try {
                    connections.record(caller, definition.name(), McpAccountingFacade.number(args, "ledgerId"), requestId,
                            transactionId, error == null ? "SUCCESS" : "ERROR", error, (System.nanoTime() - start) / 1_000_000);
                } catch (RuntimeException e) {
                    // A successful write already has atomic CREATED provenance. Do not hide its receipt.
                    log.warn("Unable to record MCP invocation metadata ({})", e.getClass().getSimpleName());
                }
            }
        }
    }
}
