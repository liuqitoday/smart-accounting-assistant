package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.ChangePasswordRequest;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.UserRepository;
import com.liuqitech.accountingassistant.security.LegacyCompatiblePasswordEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerService ledgerService;

    private final PasswordEncoder passwordEncoder = new LegacyCompatiblePasswordEncoder();

    @Test
    void changePasswordUpdatesStoredHashWhenCurrentPasswordMatches() {
        UserService userService = userService();
        User user = user("demo", "oldpass1234");
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        userService.changePassword("demo", request("oldpass1234", "newpass1234"));

        verify(userRepository).save(user);
        assertNotEquals(hash("oldpass1234"), user.getPassword());
        assertTrue(passwordEncoder.matches("newpass1234", user.getPassword()));
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        UserService userService = userService();
        User user = user("demo", "oldpass1234");
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.changePassword("demo", request("wrongpass99", "newpass1234"))
        );

        assertEquals("当前密码不正确", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsSameNewPassword() {
        UserService userService = userService();
        User user = user("demo", "oldpass1234");
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.changePassword("demo", request("oldpass1234", "oldpass1234"))
        );

        assertEquals("新密码不能与当前密码相同", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    private static ChangePasswordRequest request(String currentPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        return request;
    }

    private static User user(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(hash(password));
        return user;
    }

    private UserService userService() {
        return new UserService(userRepository, ledgerService, passwordEncoder);
    }

    private static String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
