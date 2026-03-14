package com.fieldcheck.service;

import com.fieldcheck.entity.CheckTask;
import com.fieldcheck.entity.WhitelistRule;
import com.fieldcheck.entity.WhitelistRuleType;
import com.fieldcheck.entity.WhitelistType;
import com.fieldcheck.repository.WhitelistRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhitelistService {

    private final WhitelistRuleRepository whitelistRuleRepository;

    public List<WhitelistRule> getGlobalWhitelist() {
        return whitelistRuleRepository.findByEnabled(true);
    }

    public Page<WhitelistRule> getWhitelistRules(Pageable pageable) {
        return whitelistRuleRepository.findAll(pageable);
    }

    public WhitelistRule getRule(Long id) {
        return whitelistRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("白名单规则不存在"));
    }

    @Transactional
    public WhitelistRule createRule(WhitelistRule rule) {
        if (whitelistRuleRepository.existsByRule(rule.getRule())) {
            throw new RuntimeException("规则已存在");
        }
        
        // Auto-detect rule type
        rule.setRuleType(detectRuleType(rule.getRule()));
        
        return whitelistRuleRepository.save(rule);
    }

    @Transactional
    public WhitelistRule updateRule(Long id, WhitelistRule ruleData) {
        WhitelistRule rule = getRule(id);
        rule.setRule(ruleData.getRule());
        rule.setRuleType(detectRuleType(ruleData.getRule()));
        rule.setEnabled(ruleData.getEnabled());
        rule.setRemark(ruleData.getRemark());
        return whitelistRuleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        whitelistRuleRepository.deleteById(id);
    }

    public boolean isWhitelisted(String database, String table, String field, CheckTask task) {
        Set<String> rules = new HashSet<>();
        
        // Collect rules based on task whitelist type
        if (task.getWhitelistType() == WhitelistType.GLOBAL) {
            List<WhitelistRule> globalRules = getGlobalWhitelist();
            rules.addAll(globalRules.stream().map(WhitelistRule::getRule).collect(Collectors.toSet()));
        } else if (task.getWhitelistType() == WhitelistType.CUSTOM && task.getCustomWhitelist() != null) {
            rules.addAll(parseCustomWhitelist(task.getCustomWhitelist()));
        }
        
        if (rules.isEmpty()) {
            return false;
        }
        
        // Check each rule
        for (String rule : rules) {
            if (matchRule(rule, database, table, field)) {
                return true;
            }
        }
        
        return false;
    }

    private Set<String> parseCustomWhitelist(String whitelist) {
        Set<String> rules = new HashSet<>();
        if (whitelist == null || whitelist.isEmpty()) {
            return rules;
        }
        
        for (String line : whitelist.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                rules.add(trimmed);
            }
        }
        return rules;
    }

    private boolean matchRule(String rule, String database, String table, String field) {
        String[] parts = rule.split("\\.");
        
        if (parts.length == 1) {
            // Database level: "db" or "db*"
            return matchPattern(parts[0], database);
        } else if (parts.length == 2) {
            // Table level: "db.table" or "db.*"
            return matchPattern(parts[0], database) && matchPattern(parts[1], table);
        } else if (parts.length == 3) {
            // Field level: "db.table.field"
            return matchPattern(parts[0], database) && 
                   matchPattern(parts[1], table) && 
                   (field == null || matchPattern(parts[2], field));
        }
        
        return false;
    }

    private boolean matchPattern(String pattern, String value) {
        if (pattern == null || value == null) return false;
        if (pattern.equals("*")) return true;
        
        // Convert wildcard to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        
        try {
            return Pattern.matches("(?i)" + regex, value);
        } catch (Exception e) {
            return pattern.equalsIgnoreCase(value);
        }
    }

    private WhitelistRuleType detectRuleType(String rule) {
        int dotCount = rule.length() - rule.replace(".", "").length();
        if (dotCount == 0) {
            return WhitelistRuleType.DATABASE;
        } else if (dotCount == 1) {
            return WhitelistRuleType.TABLE;
        } else {
            return WhitelistRuleType.FIELD;
        }
    }
}
