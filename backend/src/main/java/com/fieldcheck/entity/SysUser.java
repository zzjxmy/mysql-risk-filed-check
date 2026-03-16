package com.fieldcheck.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sys_user")
@EqualsAndHashCode(callSuper = true)
public class SysUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column
    private String password;

    @Column(length = 100)
    private String nickname;

    @Column(length = 200)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", length = 20)
    @Builder.Default
    private AuthType authType = AuthType.LOCAL;

    @Column(name = "ldap_dn", length = 500)
    private String ldapDn;  // LDAP Distinguished Name

    public enum AuthType {
        LOCAL,  // Local database user
        LDAP    // LDAP/AD user
    }
}
