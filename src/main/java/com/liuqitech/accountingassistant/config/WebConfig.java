package com.liuqitech.accountingassistant.config;

import com.liuqitech.accountingassistant.interceptor.AuthenticatedUserInterceptor;
import com.liuqitech.accountingassistant.interceptor.LedgerContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthenticatedUserInterceptor authenticatedUserInterceptor;
    private final LedgerContextInterceptor ledgerContextInterceptor;

    public WebConfig(AuthenticatedUserInterceptor authenticatedUserInterceptor,
                     LedgerContextInterceptor ledgerContextInterceptor) {
        this.authenticatedUserInterceptor = authenticatedUserInterceptor;
        this.ledgerContextInterceptor = ledgerContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 身份上下文：Spring Security 完成认证后，将 Principal 写入统一的 LedgerContext。
        registry.addInterceptor(authenticatedUserInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register", "/api/auth/csrf");

        // 2. 账本上下文：仅账本作用域接口，解析当前账本 + 成员/角色门禁
        //    /api/ledgers/** 不挂此拦截器（其按路径 {id} 自行鉴权）；/api/categories/** 为全局数据。
        registry.addInterceptor(ledgerContextInterceptor)
                .addPathPatterns("/api/transactions/**", "/api/transfers/**", "/api/tags/**",
                        "/api/statistics/**", "/api/accounts/**", "/api/recurring-bills/**", "/api/analysis/**");
    }
}
