package com.fieldcheck.config;

import com.fieldcheck.entity.SysUser;
import com.fieldcheck.security.CustomUserDetailsService;
import com.fieldcheck.service.LdapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;

@Slf4j
@RequiredArgsConstructor
public class LdapAuthenticationProvider implements AuthenticationProvider {

    private final LdapService ldapService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();
        
        // Check if this is an LDAP login request (stored in details)
        Object details = authentication.getDetails();
        boolean isLdapLogin = false;
        if (details instanceof java.util.Map) {
            Object useLdap = ((java.util.Map<?, ?>) details).get("useLdap");
            isLdapLogin = Boolean.TRUE.equals(useLdap);
        }
        
        // Only attempt LDAP authentication if explicitly requested
        // If not LDAP login, return null to let other providers handle it
        if (!isLdapLogin) {
            return null;
        }

        log.debug("Attempting LDAP authentication for user: {}", username);

        // Try LDAP authentication
        if (ldapService.authenticate(username, password)) {
            log.info("LDAP authentication successful for user: {}", username);
            
            // Get user info from LDAP
            LdapService.LdapUserInfo ldapInfo = ldapService.getUserInfo(username);
            if (ldapInfo != null) {
                log.info("Got LDAP user info for {}: dn={}, email={}, displayName={}", 
                        username, ldapInfo.getDn(), ldapInfo.getEmail(), ldapInfo.getDisplayName());
                
                // Sync user to local database
                SysUser user = ldapService.syncUserToDatabase(ldapInfo);
                log.info("Synced LDAP user to database: {}, role={}", user.getUsername(), user.getRole());
                
                // Load user details for JWT token generation
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                log.info("Loaded user details for {}: authorities={}", username, userDetails.getAuthorities());
                
                return new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
                );
            } else {
                log.error("Failed to get LDAP user info for {} after successful authentication", username);
            }
        }

        throw new BadCredentialsException("LDAP authentication failed for user: " + username);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
