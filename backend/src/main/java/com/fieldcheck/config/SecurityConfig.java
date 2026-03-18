package com.fieldcheck.config;

import com.fieldcheck.security.CustomUserDetailsService;
import com.fieldcheck.security.JwtAuthenticationFilter;
import com.fieldcheck.service.LdapService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final LdapService ldapService;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        // Local authentication provider
        DaoAuthenticationProvider localProvider = new DaoAuthenticationProvider();
        localProvider.setUserDetailsService(userDetailsService);
        localProvider.setPasswordEncoder(passwordEncoder());
        auth.authenticationProvider(localProvider);
        
        // LDAP authentication provider (always registered, will check if enabled internally)
        auth.authenticationProvider(ldapAuthenticationProvider());
    }
    
    @Bean
    public AuthenticationProvider ldapAuthenticationProvider() {
        return new LdapAuthenticationProvider(ldapService, userDetailsService);
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors().and().csrf().disable()
                .headers().frameOptions().disable().and()  // Disable default X-Frame-Options (nginx handles it)
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/api/ldap-config").permitAll()  // Allow public access to check LDAP status
                .antMatchers("/ws/**").permitAll()
                .antMatchers("/actuator/**").permitAll()
                .antMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
                .and()
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
