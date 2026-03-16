package com.fieldcheck.service;

import com.fieldcheck.entity.SysLdapConfig;
import com.fieldcheck.entity.SysUser;
import com.fieldcheck.entity.UserRole;
import com.fieldcheck.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LdapService {

    private final LdapConfigService ldapConfigService;
    private final SysUserRepository userRepository;

    public boolean isEnabled() {
        SysLdapConfig config = ldapConfigService.getConfig();
        return config != null && Boolean.TRUE.equals(config.getEnabled());
    }
    
    /**
     * Get LDAP template (lazy initialization)
     */
    private LdapTemplate getLdapTemplate() {
        SysLdapConfig config = ldapConfigService.getConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        
        try {
            LdapContextSource contextSource = new LdapContextSource();
            contextSource.setUrl(config.getUrl());
            contextSource.setBase(config.getBaseDn());
            contextSource.setUserDn(config.getUsername());
            contextSource.setPassword(ldapConfigService.getDecryptedPassword(config));
            contextSource.afterPropertiesSet();
            
            LdapTemplate template = new LdapTemplate(contextSource);
            template.setIgnorePartialResultException(true);
            return template;
        } catch (Exception e) {
            log.error("Failed to create LDAP template: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Authenticate user against LDAP
     */
    public boolean authenticate(String username, String password) {
        SysLdapConfig config = ldapConfigService.getConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            log.warn("LDAP not enabled or config not found");
            return false;
        }
        
        try {
            // Try 1: Build user DN for authentication
            String userDn = buildUserDn(username, config);
            log.info("Attempting LDAP authentication for user: {}, DN: {}, URL: {}", username, userDn, config.getUrl());
            
            if (tryAuthenticate(config.getUrl(), userDn, password)) {
                log.info("LDAP authentication successful for user: {} using DN", username);
                return true;
            }
        } catch (Exception e) {
            log.warn("LDAP DN authentication failed for user {}: {}", username, e.getMessage());
        }
        
        // Try 2: Try with just username@domain format (Active Directory style)
        try {
            String userPrincipalName = username + "@" + config.getBaseDn().replace("dc=", "").replace(",", ".");
            log.info("Attempting LDAP authentication with UPN: {}", userPrincipalName);
            
            if (tryAuthenticate(config.getUrl(), userPrincipalName, password)) {
                log.info("LDAP authentication successful for user: {} using UPN", username);
                return true;
            }
        } catch (Exception e) {
            log.warn("LDAP UPN authentication failed for user {}: {}", username, e.getMessage());
        }
        
        // Try 3: Try with DOMAIN\\username format (Windows style)
        try {
            String domain = config.getBaseDn().replace("dc=", "").replace(",", ".");
            String[] parts = domain.split("\\.");
            String domainName = parts.length > 0 ? parts[0] : domain;
            String domainUser = domainName + "\\" + username;
            log.info("Attempting LDAP authentication with domain\\user: {}", domainUser);
            
            if (tryAuthenticate(config.getUrl(), domainUser, password)) {
                log.info("LDAP authentication successful for user: {} using domain\\user", username);
                return true;
            }
        } catch (Exception e) {
            log.warn("LDAP domain\\user authentication failed for user {}: {}", username, e.getMessage());
        }
        
        log.error("All LDAP authentication attempts failed for user: {}", username);
        return false;
    }
    
    private boolean tryAuthenticate(String url, String userDn, String password) throws Exception {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(url);
        contextSource.setUserDn(userDn);
        contextSource.setPassword(password);
        contextSource.afterPropertiesSet();
        
        // Test connection with user credentials
        contextSource.getReadOnlyContext();
        return true;
    }

    /**
     * Get user info from LDAP
     */
    public LdapUserInfo getUserInfo(String username) {
        SysLdapConfig config = ldapConfigService.getConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            log.warn("LDAP config not available or not enabled");
            return null;
        }
        
        LdapTemplate ldapTemplate = getLdapTemplate();
        if (ldapTemplate == null) {
            log.error("Failed to create LDAP template");
            return null;
        }
        
        try {
            String searchBase = config.getUserSearchBase();
            // Support both {0} and %s placeholder formats
            String filter = config.getUserSearchFilter()
                    .replace("{0}", username)
                    .replace("%s", username);
            final String emailAttr = config.getEmailAttribute();
            final String displayNameAttr = config.getDisplayNameAttribute();
            
            log.info("Searching LDAP for user: {}, searchBase: {}, filter: {}", username, searchBase, filter);
            
            List<LdapUserInfo> results = ldapTemplate.search(
                searchBase,
                filter,
                new AttributesMapper<LdapUserInfo>() {
                    @Override
                    public LdapUserInfo mapFromAttributes(Attributes attrs) throws NamingException {
                        LdapUserInfo info = new LdapUserInfo();
                        info.setUsername(username);
                        
                        Attribute dnAttr = attrs.get("distinguishedName");
                        if (dnAttr != null) {
                            info.setDn((String) dnAttr.get());
                        } else {
                            log.warn("distinguishedName attribute not found for user: {}", username);
                            info.setDn("");
                        }
                        
                        if (emailAttr != null && attrs.get(emailAttr) != null) {
                            info.setEmail((String) attrs.get(emailAttr).get());
                        }
                        
                        if (displayNameAttr != null && attrs.get(displayNameAttr) != null) {
                            info.setDisplayName((String) attrs.get(displayNameAttr).get());
                        } else {
                            // Fallback to cn or uid
                            if (attrs.get("cn") != null) {
                                info.setDisplayName((String) attrs.get("cn").get());
                            } else {
                                info.setDisplayName(username);
                            }
                        }
                        
                        return info;
                    }
                }
            );
            
            log.info("LDAP search returned {} results for user: {}", results.size(), username);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.error("Failed to get LDAP user info for {}: {}", username, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build user DN for authentication
     */
    private String buildUserDn(String username, SysLdapConfig config) {
        String searchBase = config.getUserSearchBase();
        String filter = config.getUserSearchFilter();
        
        // Common patterns:
        // uid={0} -> uid=username,ou=users,dc=example,dc=com
        // sAMAccountName={0} -> CN=username,ou=users,dc=example,dc=com (Active Directory)
        
        // Extract attribute name from filter like "(sAMAccountName={0})" or "uid={0}"
        String attribute = filter.replaceAll("\\(?(\\w+)=\\{0\\}\\)?", "$1");
        
        String userDn;
        if (searchBase != null && !searchBase.isEmpty()) {
            userDn = String.format("%s=%s,%s,%s", attribute, username, searchBase, config.getBaseDn());
        } else {
            userDn = String.format("%s=%s,%s", attribute, username, config.getBaseDn());
        }
        
        log.debug("Built user DN: {} (filter: {}, searchBase: {}, baseDn: {})", 
                userDn, filter, searchBase, config.getBaseDn());
        return userDn;
    }

    /**
     * Sync LDAP user to local database
     */
    public SysUser syncUserToDatabase(LdapUserInfo ldapInfo) {
        SysLdapConfig config = ldapConfigService.getConfig();
        String defaultRole = (config != null && config.getDefaultRole() != null) 
                ? config.getDefaultRole() : "USER";
        
        Optional<SysUser> existingUser = userRepository.findByUsername(ldapInfo.getUsername());
        
        SysUser user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            // Update existing LDAP user info
            if (user.getAuthType() == SysUser.AuthType.LDAP) {
                user.setEmail(ldapInfo.getEmail());
                user.setNickname(ldapInfo.getDisplayName());
                user.setLdapDn(ldapInfo.getDn());
            }
        } else {
            // Create new user from LDAP
            user = SysUser.builder()
                    .username(ldapInfo.getUsername())
                    .password("")  // No local password for LDAP users
                    .email(ldapInfo.getEmail())
                    .nickname(ldapInfo.getDisplayName())
                    .ldapDn(ldapInfo.getDn())
                    .authType(SysUser.AuthType.LDAP)
                    .role(UserRole.valueOf(defaultRole))
                    .enabled(true)
                    .build();
        }
        
        user.setLastLoginTime(LocalDateTime.now());
        return userRepository.save(user);
    }

    /**
     * LDAP user information
     */
    @lombok.Data
    public static class LdapUserInfo {
        private String username;
        private String dn;
        private String email;
        private String displayName;
    }
}
