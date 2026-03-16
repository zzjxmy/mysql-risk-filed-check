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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LdapService ldapService;

    @PostConstruct
    public void init() {
        // Create or reset default admin user (always local)
        SysUser admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            admin = SysUser.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .nickname("Administrator")
                    .role(UserRole.ADMIN)
                    .enabled(true)
                    .authType(SysUser.AuthType.LOCAL)
                    .build();
        } else {
            // Reset password to ensure it's correct (only for local admin)
            if (admin.getAuthType() == SysUser.AuthType.LOCAL || admin.getAuthType() == null) {
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setEnabled(true);
            }
        }
        userRepository.save(admin);
        log.info("Default admin user ready: admin/admin123");
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        boolean useLdap = Boolean.TRUE.equals(request.getUseLdap());
        
        // Check if user exists and determine auth type
        Optional<SysUser> existingUser = userRepository.findByUsername(username);
        
        if (existingUser.isPresent()) {
            SysUser user = existingUser.get();
            
            // If user explicitly requests LDAP login
            if (useLdap) {
                if (!ldapService.isEnabled()) {
                    throw new RuntimeException("LDAP认证未启用");
                }
                // LDAP authentication will be handled by LdapAuthenticationProvider
            }
            // If user is LDAP type but not using LDAP login, reject
            else if (user.getAuthType() == SysUser.AuthType.LDAP) {
                throw new RuntimeException("LDAP用户请使用LDAP登录");
            }
            // Local user - will be handled by DaoAuthenticationProvider
        } else {
            // New user - try LDAP if enabled and requested
            if (useLdap) {
                if (!ldapService.isEnabled()) {
                    throw new RuntimeException("LDAP认证未启用");
                }
                log.info("New user {}, attempting LDAP authentication", username);
            }
        }
        
        // Authenticate using Spring Security (will try all providers)
        // Create auth token with useLdap flag in details
        UsernamePasswordAuthenticationToken authRequest = 
                new UsernamePasswordAuthenticationToken(username, password);
        Map<String, Object> details = new HashMap<>();
        details.put("useLdap", useLdap);
        authRequest.setDetails(details);
        
        Authentication authentication = authenticationManager.authenticate(authRequest);

        // Get user (either existing or newly created by LDAP sync)
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found after authentication"));

        // Update last login time
        user.setLastLoginTime(LocalDateTime.now());
        userRepository.save(user);

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
    
    public boolean isLdapEnabled() {
        return ldapService.isEnabled();
    }
}
