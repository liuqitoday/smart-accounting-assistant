package com.liuqitech.accountingassistant.config;

import com.liuqitech.accountingassistant.security.DatabaseUserDetailsService;
import com.liuqitech.accountingassistant.security.GracePeriodRememberMeServices;
import com.liuqitech.accountingassistant.security.JsonAccessDeniedHandler;
import com.liuqitech.accountingassistant.security.JsonAuthenticationEntryPoint;
import com.liuqitech.accountingassistant.security.LegacyCompatiblePasswordEncoder;
import com.liuqitech.accountingassistant.security.SessionEpochFilter;
import com.liuqitech.accountingassistant.security.SessionRevocationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import javax.sql.DataSource;
import java.time.Duration;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JsonAuthenticationEntryPoint authenticationEntryPoint,
                          JsonAccessDeniedHandler accessDeniedHandler) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   GracePeriodRememberMeServices rememberMeServices,
                                                   SessionRevocationRegistry revocationRegistry,
                                                   @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // XSRF cookie 与会话 cookie 共用同一 Secure 开关（COOKIE_SECURE，生产 HTTPS 置 true）
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookie));

        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                .headers(headers -> headers
                        // 同源 SPA：脚本仅限自身（构建产物无内联脚本/外部 CDN）；
                        // Vue 动态样式需 style 'unsafe-inline'，图标/字体放行 data:
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; connect-src 'self'; font-src 'self' data:"))
                        // HSTS 一年 + 子域（Spring Security 默认仅在 HTTPS 请求上发送该头，HTTP 本地不受影响）
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(sessionFixation -> sessionFixation.migrateSession())
                )
                .rememberMe(remember -> remember.rememberMeServices(rememberMeServices))
                .addFilterBefore(new SessionEpochFilter(revocationRegistry), AuthorizationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/login",
                                "/login.html",
                                "/dashboard",
                                "/transactions",
                                "/statistics",
                                "/accounts",
                                "/tags",
                                "/ledgers",
                                "/profile",
                                "/assets/**",
                                "/favicon.svg",
                                "/apple-touch-icon.png",
                                "/pwa-*.png",
                                "/manifest.webmanifest",
                                "/registerSW.js",
                                "/sw.js",
                                "/workbox-*.js"
                        ).permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/csrf").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new LegacyCompatiblePasswordEncoder();
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        // 建表由 SchemaIndexInitializer 负责（其 CREATE TABLE 语法兼容 SQLite/H2，框架自带的不幂等）
        return repository;
    }

    @Bean
    public GracePeriodRememberMeServices rememberMeServices(
            @Value("${app.security.remember-me.key}") String key,
            @Value("${app.security.remember-me.validity}") Duration validity,
            DatabaseUserDetailsService userDetailsService,
            PersistentTokenRepository tokenRepository) {
        GracePeriodRememberMeServices services =
                new GracePeriodRememberMeServices(key, userDetailsService, tokenRepository, 60_000L);
        services.setAlwaysRemember(true); // JSON 登录无 remember-me 勾选参数，始终下发
        services.setTokenValiditySeconds((int) validity.getSeconds());
        return services;
    }
}
