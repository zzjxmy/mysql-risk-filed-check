package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.dto.LoginRequest;
import com.fieldcheck.dto.LoginResponse;
import com.fieldcheck.entity.SysUser;
import com.fieldcheck.service.AuthService;
import com.fieldcheck.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditLogService auditLogService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest) {
        try {
            LoginResponse response = authService.login(request);
            auditLogService.logLogin(request.getUsername(), httpRequest, true, "登录成功");
            return ApiResponse.success(response);
        } catch (Exception e) {
            auditLogService.logLogin(request.getUsername(), httpRequest, false, "登录失败: " + e.getMessage());
            return ApiResponse.error(401, "用户名或密码错误");
        }
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        SysUser user = authService.getCurrentUser(userDetails.getUsername());
        LoginResponse response = LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole().name())
                .build();
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        auditLogService.logAsync("LOGOUT", "Auth", null, null, "用户登出", request);
        return ApiResponse.success("登出成功", null);
    }
}
