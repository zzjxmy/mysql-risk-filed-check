package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.dto.LdapConfigDTO;
import com.fieldcheck.entity.SysLdapConfig;
import com.fieldcheck.service.AuditLogService;
import com.fieldcheck.service.LdapConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/ldap-config")
@RequiredArgsConstructor
public class LdapConfigController {

    private final LdapConfigService ldapConfigService;
    private final AuditLogService auditLogService;

    /**
     * Get LDAP configuration status (Public - for login page)
     * Returns only enabled status, no sensitive info
     */
    @GetMapping
    public ApiResponse<java.util.Map<String, Boolean>> getConfig() {
        SysLdapConfig config = ldapConfigService.getConfig();
        java.util.Map<String, Boolean> result = new java.util.HashMap<>();
        result.put("enabled", config != null && Boolean.TRUE.equals(config.getEnabled()));
        return ApiResponse.success(result);
    }
    
    /**
     * Get full LDAP configuration (Admin only)
     */
    @GetMapping("/detail")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LdapConfigDTO> getConfigDetail() {
        LdapConfigDTO config = ldapConfigService.getConfigDTO();
        return ApiResponse.success(config);
    }

    /**
     * Save LDAP configuration (Admin only)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LdapConfigDTO> saveConfig(@Valid @RequestBody LdapConfigDTO dto,
                                                  HttpServletRequest request) {
        ldapConfigService.saveConfig(dto);
        auditLogService.logAsync("UPDATE", "LdapConfig", null, null, 
                "更新LDAP配置, enabled: " + dto.getEnabled(), request);
        
        // Return saved config (without password)
        return ApiResponse.success("LDAP配置保存成功", ldapConfigService.getConfigDTO());
    }

    /**
     * Test LDAP connection (Admin only)
     */
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> testConnection(@RequestBody LdapConfigDTO dto,
                                               HttpServletRequest request) {
        String errorMsg = ldapConfigService.testConnection(dto);
        
        auditLogService.logAsync("TEST", "LdapConfig", null, null, 
                "测试LDAP连接: " + (errorMsg == null ? "成功" : "失败 - " + errorMsg), request);
        
        if (errorMsg == null) {
            return ApiResponse.success("LDAP连接测试成功", null);
        } else {
            return ApiResponse.error(400, "LDAP连接测试失败: " + errorMsg);
        }
    }
}
