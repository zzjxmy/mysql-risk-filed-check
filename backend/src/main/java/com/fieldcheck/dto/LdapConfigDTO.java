package com.fieldcheck.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class LdapConfigDTO {
    
    private Long id;
    
    private Boolean enabled;
    
    @NotBlank(message = "LDAP服务器地址不能为空")
    private String url;
    
    @NotBlank(message = "Base DN不能为空")
    private String baseDn;
    
    private String username;
    
    private String password;  // 写入时传入，读取时不返回
    
    private String userSearchBase;
    
    private String userSearchFilter;
    
    private String groupSearchBase;
    
    private String groupSearchFilter;
    
    private String emailAttribute;
    
    private String displayNameAttribute;
    
    private String defaultRole;
    
    private String description;
}
