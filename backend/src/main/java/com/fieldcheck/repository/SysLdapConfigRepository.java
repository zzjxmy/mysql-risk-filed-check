package com.fieldcheck.repository;

import com.fieldcheck.entity.SysLdapConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysLdapConfigRepository extends JpaRepository<SysLdapConfig, Long> {
    
    /**
     * Get the first (and only) LDAP config
     */
    default SysLdapConfig getConfig() {
        return findFirstByOrderByIdAsc().orElse(null);
    }
    
    java.util.Optional<SysLdapConfig> findFirstByOrderByIdAsc();
}
