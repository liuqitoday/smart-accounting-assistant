package com.liuqitech.accountingassistant.security;

import com.liuqitech.accountingassistant.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.liuqitech.accountingassistant.entity.User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .roles("USER")
                .build();
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        com.liuqitech.accountingassistant.entity.User entity = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        entity.setPassword(newPassword);
        userRepository.save(entity);
        return User.withUserDetails(user)
                .password(newPassword)
                .build();
    }
}
