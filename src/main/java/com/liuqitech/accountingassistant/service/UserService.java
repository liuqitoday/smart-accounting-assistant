package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.AuthResponse;
import com.liuqitech.accountingassistant.dto.ChangePasswordRequest;
import com.liuqitech.accountingassistant.dto.RegisterRequest;
import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, LedgerService ledgerService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (TagService.SYSTEM_OWNER.equalsIgnoreCase(request.getUsername())) {
            throw new BusinessException("该用户名不可用");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);

        // 新用户自动获得一个默认账本，并设为当前账本
        Ledger defaultLedger = ledgerService.createDefaultLedger(user.getUsername());
        user.setDefaultLedgerId(defaultLedger.getId());
        userRepository.save(user);

        return new AuthResponse(user.getUsername());
    }

    @Transactional
    public AuthResponse afterLogin(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        // 安全网：若用户当前没有任何账本（如删光了），自动补一个默认账本
        if (user.getDefaultLedgerId() == null) {
            Ledger defaultLedger = ledgerService.createDefaultLedger(user.getUsername());
            user.setDefaultLedgerId(defaultLedger.getId());
            userRepository.save(user);
        }

        return new AuthResponse(user.getUsername());
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (!verifyPassword(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("当前密码不正确");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // ── 密码验证：BCrypt 优先，兼容旧 SHA-256 ──

    public boolean verifyPassword(String rawPassword, String storedHash) {
        return passwordEncoder.matches(rawPassword, storedHash);
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
