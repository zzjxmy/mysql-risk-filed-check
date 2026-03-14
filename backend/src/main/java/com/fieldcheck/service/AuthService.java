package com.fieldcheck.service;

import com.fieldcheck.dto.LoginRequest;
import com.fieldcheck.dto.LoginResponse;
import com.fieldcheck.entity.SysUser;
import com.fieldcheck.entity.UserRole;
import com.fieldcheck.repository.SysUserRepository;
import com.fieldcheck.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        // Create or reset default admin user
        SysUser admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            admin = SysUser.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .nickname("Administrator")
                    .role(UserRole.ADMIN)
                    .enabled(true)
                    .build();
        } else {
            // Reset password to ensure it's correct
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEnabled(true);
        }
        userRepository.save(admin);
        log.info("Default admin user ready: admin/admin123");
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SysUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = tokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole().name())
                .build();
    }

    public SysUser getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
