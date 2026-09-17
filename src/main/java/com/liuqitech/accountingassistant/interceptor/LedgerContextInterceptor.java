package com.liuqitech.accountingassistant.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.repository.UserRepository;
import com.liuqitech.accountingassistant.service.LedgerAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.Set;

/**
 * 解析「当前账本」上下文并做账本级权限门禁。注册在 {@link AuthenticatedUserInterceptor} 之后，
 * 仅作用于账本作用域路径（/api/transactions、/api/tags、/api/statistics）。
 *
 * <ol>
 *     <li>从请求头 {@code X-Ledger-Id} 取当前账本，缺省时回退到用户的 default_ledger_id。</li>
 *     <li>校验登录用户是该账本成员，否则 403。</li>
 *     <li>按 HTTP 方法做角色门禁：写操作（非 GET）要求 EDITOR 及以上，读操作仅需成员（VIEWER）。</li>
 *     <li>把 ledgerId 与 ledgerRole 写入请求属性，供控制器/服务使用。</li>
 * </ol>
 */
@Component
public class LedgerContextInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LedgerContextInterceptor.class);
    private static final String LEDGER_HEADER = "X-Ledger-Id";

    private final UserRepository userRepository;
    private final LedgerAccessService ledgerAccessService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LedgerContextInterceptor(UserRepository userRepository, LedgerAccessService ledgerAccessService) {
        this.userRepository = userRepository;
        this.ledgerAccessService = ledgerAccessService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String username = LedgerContext.username(request);
        if (username == null) {
            return writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "未授权访问");
        }

        // 1. 解析当前账本：优先请求头，其次用户默认账本
        Long ledgerId = parseLedgerId(request.getHeader(LEDGER_HEADER));
        if (ledgerId == null) {
            ledgerId = userRepository.findByUsername(username)
                    .map(User::getDefaultLedgerId)
                    .orElse(null);
        }
        if (ledgerId == null) {
            return writeError(response, HttpServletResponse.SC_BAD_REQUEST, "未指定账本，请先创建或选择一个账本");
        }

        // 2. 成员校验
        Optional<LedgerMember> membershipOpt = ledgerAccessService.findMembership(username, ledgerId);
        if (membershipOpt.isEmpty()) {
            return writeError(response, HttpServletResponse.SC_FORBIDDEN, "无权访问该账本");
        }
        LedgerMember membership = membershipOpt.get();

        // 3. 按路径+方法做角色门禁：分析提问/清空自己的聊天对 VIEWER 开放
        if (requiresEditor(request) && !membership.getRole().atLeast(LedgerRole.EDITOR)) {
            return writeError(response, HttpServletResponse.SC_FORBIDDEN, "当前账本为「仅查看」权限，无法进行此操作");
        }

        // 4. 写入上下文
        request.setAttribute(LedgerContext.ATTR_LEDGER_ID, ledgerId);
        request.setAttribute(LedgerContext.ATTR_ROLE, membership.getRole());
        return true;
    }

    private boolean requiresEditor(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        if ("POST".equals(method) && "/api/analysis/chat".equals(path)) {
            return false;
        }
        if ("DELETE".equals(method) && "/api/analysis/messages".equals(path)) {
            return false;
        }
        return !Set.of("GET", "HEAD", "OPTIONS").contains(method);
    }

    private Long parseLedgerId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            logger.warn("非法的 X-Ledger-Id 请求头: {}", header);
            return null;
        }
    }

    private boolean writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
        return false;
    }
}
