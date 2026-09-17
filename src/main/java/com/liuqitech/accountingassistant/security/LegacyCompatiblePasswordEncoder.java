package com.liuqitech.accountingassistant.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 新密码统一使用 BCrypt，同时兼容早期无盐 SHA-256 密码。
 */
public class LegacyCompatiblePasswordEncoder implements PasswordEncoder {

    private static final String HASH_PREFIX_BCRYPT_2A = "$2a$";
    private static final String HASH_PREFIX_BCRYPT_2B = "$2b$";
    private static final String HASH_PREFIX_BCRYPT_2Y = "$2y$";

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (isBcrypt(encodedPassword)) {
            return delegate.matches(rawPassword, encodedPassword);
        }
        return legacySha256(rawPassword).equals(encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return !isBcrypt(encodedPassword);
    }

    private static boolean isBcrypt(String encodedPassword) {
        return encodedPassword != null
                && (encodedPassword.startsWith(HASH_PREFIX_BCRYPT_2A)
                || encodedPassword.startsWith(HASH_PREFIX_BCRYPT_2B)
                || encodedPassword.startsWith(HASH_PREFIX_BCRYPT_2Y));
    }

    private static String legacySha256(CharSequence password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }
}
