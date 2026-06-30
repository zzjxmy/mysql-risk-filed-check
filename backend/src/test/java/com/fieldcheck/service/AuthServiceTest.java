package com.fieldcheck.service;

import com.fieldcheck.entity.SysUser;
import com.fieldcheck.entity.UserRole;
import com.fieldcheck.repository.SysUserRepository;
import com.fieldcheck.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LdapService ldapService;

    @InjectMocks
    private AuthService authService;

    @Test
    void initDoesNotOverwriteExistingLocalAdminPassword() {
        SysUser admin = SysUser.builder()
                .username("admin")
                .password("custom-password-hash")
                .nickname("Administrator")
                .role(UserRole.ADMIN)
                .enabled(true)
                .authType(SysUser.AuthType.LOCAL)
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        authService.init();

        assertThat(admin.getPassword()).isEqualTo("custom-password-hash");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(SysUser.class));
    }
}
