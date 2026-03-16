package com.fieldcheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ldap")
public class LdapProperties {
    
    private boolean enabled = false;
    
    private String url;
    
    private String baseDn;
    
    private String username;
    
    private String password;
    
    private String userSearchBase;
    
    private String userSearchFilter = "(uid={0})";
    
    private String groupSearchBase;
    
    private String groupSearchFilter;
    
    private String emailAttribute = "mail";
    
    private String displayNameAttribute = "displayName";
    
    private String defaultRole = "USER";
}
