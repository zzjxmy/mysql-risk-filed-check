package com.fieldcheck.repository;

import com.fieldcheck.entity.DbConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DbConnectionRepository extends JpaRepository<DbConnection, Long> {
    
    @Query("SELECT c FROM DbConnection c WHERE " +
           "(:name IS NULL OR c.name LIKE %:name%) AND " +
           "(:enabled IS NULL OR c.enabled = :enabled)")
    Page<DbConnection> findByConditions(@Param("name") String name, 
                                        @Param("enabled") Boolean enabled,
                                        Pageable pageable);
    
    List<DbConnection> findByEnabled(Boolean enabled);
    
    boolean existsByName(String name);
}
