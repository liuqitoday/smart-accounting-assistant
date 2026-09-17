package com.liuqitech.accountingassistant.controller;

import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.dto.AuthResponse;
import com.liuqitech.accountingassistant.dto.ChangePasswordRequest;
import com.liuqitech.accountingassistant.dto.LoginRequest;
import com.liuqitech.accountingassistant.dto.RegisterRequest;
import com.liuqitech.accountingassistant.interceptor.LedgerContext;
import com.liuqitech.accountingassistant.security.GracePeriodRememberMeServices;
import com.liuqitech.accountingassistant.security.LoginAttemptService;
import com.liuqitech.accountingassistant.security.SessionEpochFilter;
import com.liuqitech.accountingassistant.security.SessionRevocationRegistry;
import com.liuqitech.accountingassistant.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import com.liuqitech.accountingassistant.exception.BusinessException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final GracePeriodRememberMeServices rememberMeServices;
    private final PersistentTokenRepository tokenRepository;
    private final SessionRevocationRegistry revocationRegistry;
    private final LoginAttemptService loginAttemptService;

    public AuthController(UserService userService,
                          AuthenticationManager authenticationManager,
                          GracePeriodRememberMeServices rememberMeServices,
                          PersistentTokenRepository tokenRepository,
                          SessionRevocationRegistry revocationRegistry,
                          LoginAttemptService loginAttemptService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.rememberMeServices = rememberMeServices;
        this.tokenRepository = tokenRepository;
        this.revocationRegistry = revocationRegistry;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        loginAttemptService.checkRegistrationAllowed(loginAttemptService.clientIp(httpRequest));
        AuthResponse response = userService.register(request);
        Authentication authentication = authenticateSession(request.getUsername(), request.getPassword(), httpRequest);
        rememberMeServices.loginSuccess(httpRequest, httpResponse, authentication);
        return ApiResponse.success(response);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        String clientIp = loginAttemptService.clientIp(httpRequest);
        loginAttemptService.checkLoginAllowed(request.getUsername(), clientIp);
        Authentication authentication;
        try {
            authentication = authenticateSession(request.getUsername(), request.getPassword(), httpRequest);
        } catch (BusinessException e) {
            // 只计爆破尝试后原样重抛（非 catch-and-wrap，错误语义不变）
            loginAttemptService.recordLoginFailure(request.getUsername(), clientIp);
            throw e;
        }
        loginAttemptService.recordLoginSuccess(request.getUsername(), clientIp);
        rememberMeServices.loginSuccess(httpRequest, httpResponse, authentication);
        return ApiResponse.success(userService.afterLogin(authentication.getName()));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse> me(Authentication authentication) {
        return ApiResponse.success(new AuthResponse(authentication.getName()));
    }

    @GetMapping("/csrf")
    public ApiResponse<Void> csrf(CsrfToken csrfToken) {
        // 访问此接口会触发 Spring Security 写入 XSRF-TOKEN cookie。
        csrfToken.getToken();
        return ApiResponse.success(null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            revocationRegistry.revoke(authentication.getName()); // 其他设备的活跃会话一并吊销
        }
        rememberMeServices.logout(httpRequest, httpResponse, authentication);
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        expireCookie(httpResponse, "JSESSIONID");
        expireCookie(httpResponse, "XSRF-TOKEN");
        return ApiResponse.success(null, "已退出登录");
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse httpResponse) {
        String username = LedgerContext.username(httpRequest);
        userService.changePassword(username, request);
        tokenRepository.removeUserTokens(username);
        long freshEpoch = revocationRegistry.revoke(username); // 踢其他设备的会话与令牌
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.setAttribute(SessionEpochFilter.AUTH_EPOCH, freshEpoch); // 当前设备保活
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        rememberMeServices.loginSuccess(httpRequest, httpResponse, authentication);
        return ApiResponse.success(null, "密码已更新");
    }

    private Authentication authenticateSession(String username, String password, HttpServletRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password)
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                request.changeSessionId();
            }
            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            session.setAttribute(SessionEpochFilter.AUTH_EPOCH, revocationRegistry.nextEpoch());
            request.setAttribute(LedgerContext.ATTR_USERNAME, authentication.getName());

            return authentication;
        } catch (AuthenticationException e) {
            throw new BusinessException("用户名或密码错误");
        }
    }

    private void expireCookie(HttpServletResponse response, String name) {
        response.addHeader("Set-Cookie", name + "=; Max-Age=0; Path=/; SameSite=Lax");
    }
}
