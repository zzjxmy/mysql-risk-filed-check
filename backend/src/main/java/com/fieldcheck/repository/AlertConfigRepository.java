package com.fieldcheck.repository;

import com.fieldcheck.entity.AlertConfig;
import com.fieldcheck.entity.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertConfigRepository extends JpaRepository<AlertConfig, Long> {
    
    List<AlertConfig> findByEnabled(Boolean enabled);
    
    List<AlertConfig> findByAlertTypeAndEnabled(AlertType alertType, Boolean enabled);
    
    boolean existsByName(String name);
}
