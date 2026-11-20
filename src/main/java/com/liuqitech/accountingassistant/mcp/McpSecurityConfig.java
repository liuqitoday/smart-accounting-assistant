package com.liuqitech.accountingassistant.mcp;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Configuration
public class McpSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurityChain(HttpSecurity http, McpConnectionService connections,
            @Value("${spring.ai.mcp.server.enabled:true}") boolean enabled,
            @Value("${app.mcp.allowed-hosts:localhost,127.0.0.1,[::1]}") String hosts,
            @Value("${app.mcp.allowed-origins:}") String origins) throws Exception {
        // This filter is intentionally not a servlet Filter bean: it only runs in this chain.
        var filter = new TokenFilter(connections, enabled, csv(hosts), csv(origins));
        return http.securityMatcher("/mcp", "/mcp/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
                .formLogin(f -> f.disable()).httpBasic(b -> b.disable()).logout(l -> l.disable())
                .addFilterBefore(filter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> failure(res, 401, "UNAUTHORIZED")))
                .build();
    }

    private static Set<String> csv(String value) {
        return new HashSet<>(Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private static void failure(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer realm=\"accounting-mcp\"");
        if (status == 429) response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"error\":\"" + code + "\"}");
    }

    private static final class TokenFilter extends OncePerRequestFilter {
        private static final int MAX_BYTES = 65536;
        private final McpConnectionService connections;
        private final boolean enabled;
        private final Set<String> hosts;
        private final Set<String> origins;
        private final Map<String, Window> limits = new HashMap<>();

        TokenFilter(McpConnectionService connections, boolean enabled, Set<String> hosts, Set<String> origins) {
            this.connections = connections;
            this.enabled = enabled;
            this.hosts = hosts;
            this.origins = origins;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (!enabled) { failure(response, 404, "MCP_DISABLED"); return; }
            if (!hosts.contains(request.getServerName().toLowerCase(Locale.ROOT))) {
                failure(response, 403, "HOST_NOT_ALLOWED"); return;
            }
            String origin = request.getHeader("Origin");
            if (origin != null && !allowedOrigin(request, origin)) {
                failure(response, 403, "ORIGIN_NOT_ALLOWED"); return;
            }
            String header = request.getHeader("Authorization");
            McpCaller caller = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                    ? connections.authenticate(header.substring(7)).orElse(null) : null;
            if (caller == null) { failure(response, 401, "UNAUTHORIZED"); return; }
            if (!acquire(caller.username())) { failure(response, 429, "RATE_LIMITED"); return; }
            try {
                if (request.getContentLengthLong() > MAX_BYTES) { failure(response, 413, "REQUEST_TOO_LARGE"); return; }
                byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
                if (body.length > MAX_BYTES) { failure(response, 413, "REQUEST_TOO_LARGE"); return; }
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(caller.username(), null, List.of()));
                SecurityContextHolder.setContext(context);
                request.setAttribute(McpCaller.CONTEXT_KEY, caller);
                response.setHeader("Cache-Control", "no-store");
                chain.doFilter(new BufferedRequest(request, body), response);
            } finally {
                release(caller.username());
                SecurityContextHolder.clearContext();
            }
        }

        private boolean allowedOrigin(HttpServletRequest req, String origin) {
            if (origins.contains(origin)) return true;
            try {
                URI uri = URI.create(origin);
                int port = uri.getPort() < 0 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
                return uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                        && (uri.getPath() == null || uri.getPath().isEmpty())
                        && req.getScheme().equals(uri.getScheme()) && req.getServerName().equalsIgnoreCase(uri.getHost())
                        && req.getServerPort() == port;
            } catch (IllegalArgumentException e) { return false; }
        }

        private synchronized boolean acquire(String username) {
            long now = System.currentTimeMillis();
            limits.entrySet().removeIf(e -> e.getValue().active == 0 && now - e.getValue().start >= 60000);
            if (!limits.containsKey(username) && limits.size() >= 10000) return false;
            Window window = limits.computeIfAbsent(username, key -> new Window(now));
            if (now - window.start >= 60000) { window.start = now; window.count = 0; }
            if (window.count >= 120 || window.active >= 4) return false;
            window.count++;
            window.active++;
            return true;
        }

        private synchronized void release(String username) { limits.get(username).active--; }
        private static final class Window {
            long start;
            int count;
            int active;
            Window(long start) { this.start = start; }
        }
    }

    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
