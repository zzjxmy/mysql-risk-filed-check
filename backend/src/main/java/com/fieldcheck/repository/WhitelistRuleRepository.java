package com.fieldcheck.repository;

import com.fieldcheck.entity.WhitelistRule;
import com.fieldcheck.entity.WhitelistRuleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WhitelistRuleRepository extends JpaRepository<WhitelistRule, Long> {
    
    List<WhitelistRule> findByEnabled(Boolean enabled);
    
    List<WhitelistRule> findByRuleTypeAndEnabled(WhitelistRuleType ruleType, Boolean enabled);
    
    Page<WhitelistRule> findByRuleContaining(String rule, Pageable pageable);
    
    boolean existsByRule(String rule);
}
