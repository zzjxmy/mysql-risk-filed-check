package com.fieldcheck.service;

import com.fieldcheck.dto.LdapConfigDTO;
import com.fieldcheck.entity.SysLdapConfig;
import com.fieldcheck.repository.SysLdapConfigRepository;
import com.fieldcheck.util.AESUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LdapConfigService {

    private final SysLdapConfigRepository ldapConfigRepository;
    
    @Value("${encryption.aes-key}")
    private String aesKey;

    /**
     * Get LDAP configuration
     */
    public SysLdapConfig getConfig() {
        return ldapConfigRepository.getConfig();
    }

    /**
     * Get LDAP configuration as DTO (password masked)
     */
    public LdapConfigDTO getConfigDTO() {
        SysLdapConfig config = getConfig();
        if (config == null) {
            return null;
        }
        
        LdapConfigDTO dto = new LdapConfigDTO();
        dto.setId(config.getId());
        dto.setEnabled(config.getEnabled());
        dto.setUrl(config.getUrl());
        dto.setBaseDn(config.getBaseDn());
        dto.setUsername(config.getUsername());
        // Don't return password
        dto.setUserSearchBase(config.getUserSearchBase());
        dto.setUserSearchFilter(config.getUserSearchFilter());
        dto.setGroupSearchBase(config.getGroupSearchBase());
        dto.setGroupSearchFilter(config.getGroupSearchFilter());
        dto.setEmailAttribute(config.getEmailAttribute());
        dto.setDisplayNameAttribute(config.getDisplayNameAttribute());
        dto.setDefaultRole(config.getDefaultRole());
        dto.setDescription(config.getDescription());
        
        return dto;
    }

    /**
     * Save LDAP configuration
     */
    @Transactional
    public SysLdapConfig saveConfig(LdapConfigDTO dto) {
        SysLdapConfig config = getConfig();
        
        if (config == null) {
            config = new SysLdapConfig();
        }
        
        config.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : false);
        config.setUrl(dto.getUrl());
        config.setBaseDn(dto.getBaseDn());
        config.setUsername(dto.getUsername());
        
        // Encrypt password if provided
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            config.setPassword(AESUtil.encrypt(dto.getPassword(), aesKey));
        } else if (config.getPassword() == null) {
            config.setPassword("");
        }
        
        config.setUserSearchBase(dto.getUserSearchBase());
        config.setUserSearchFilter(dto.getUserSearchFilter() != null ? dto.getUserSearchFilter() : "(uid={0})");
        config.setGroupSearchBase(dto.getGroupSearchBase());
        config.setGroupSearchFilter(dto.getGroupSearchFilter());
        config.setEmailAttribute(dto.getEmailAttribute() != null ? dto.getEmailAttribute() : "mail");
        config.setDisplayNameAttribute(dto.getDisplayNameAttribute() != null ? dto.getDisplayNameAttribute() : "displayName");
        config.setDefaultRole(dto.getDefaultRole() != null ? dto.getDefaultRole() : "USER");
        config.setDescription(dto.getDescription());
        
        SysLdapConfig saved = ldapConfigRepository.save(config);
        log.info("LDAP configuration saved, enabled: {}", saved.getEnabled());
        
        return saved;
    }

    /**
     * Test LDAP connection
     * @return error message if failed, null if success
     */
    public String testConnection(LdapConfigDTO dto) {
        try {
            String password = dto.getPassword();
            SysLdapConfig existingConfig = getConfig();
            
            // If password is empty, use existing encrypted password
            if ((password == null || password.isEmpty()) && existingConfig != null) {
                password = AESUtil.decrypt(existingConfig.getPassword(), aesKey);
            }
            
            org.springframework.ldap.core.support.LdapContextSource contextSource = new org.springframework.ldap.core.support.LdapContextSource();
            contextSource.setUrl(dto.getUrl());
            contextSource.setBase(dto.getBaseDn());
            contextSource.setUserDn(dto.getUsername());
            contextSource.setPassword(password);
            contextSource.afterPropertiesSet();
            
            // Test connection
            contextSource.getReadOnlyContext().close();
            
            log.info("LDAP connection test successful: {}", dto.getUrl());
            return null;  // null means success
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = e.getClass().getSimpleName();
            }
            log.error("LDAP connection test failed: {}", errorMsg);
            return errorMsg;
        }
    }

    /**
     * Get decrypted password
     */
    public String getDecryptedPassword(SysLdapConfig config) {
        if (config == null || config.getPassword() == null || config.getPassword().isEmpty()) {
            return "";
        }
        return AESUtil.decrypt(config.getPassword(), aesKey);
    }
}
