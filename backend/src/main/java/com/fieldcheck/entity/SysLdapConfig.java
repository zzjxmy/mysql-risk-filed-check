package com.fieldcheck.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sys_ldap_config")
@EqualsAndHashCode(callSuper = true)
public class SysLdapConfig extends BaseEntity {

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(nullable = false, length = 500)
    private String url;  // ldap://localhost:389

    @Column(name = "base_dn", nullable = false, length = 500)
    private String baseDn;  // dc=example,dc=com

    @Column(length = 200)
    private String username;  // cn=admin,dc=example,dc=com

    @Column(length = 200)
    private String password;  // 管理员密码（加密存储）

    @Column(name = "user_search_base", length = 200)
    private String userSearchBase;  // ou=users

    @Column(name = "user_search_filter", length = 200)
    @Builder.Default
    private String userSearchFilter = "(uid={0})";

    @Column(name = "group_search_base", length = 200)
    private String groupSearchBase;

    @Column(name = "group_search_filter", length = 200)
    private String groupSearchFilter;

    @Column(name = "email_attribute", length = 50)
    @Builder.Default
    private String emailAttribute = "mail";

    @Column(name = "display_name_attribute", length = 50)
    @Builder.Default
    private String displayNameAttribute = "displayName";

    @Column(name = "default_role", length = 20)
    @Builder.Default
    private String defaultRole = "USER";

    @Column(length = 500)
    private String description;
}
