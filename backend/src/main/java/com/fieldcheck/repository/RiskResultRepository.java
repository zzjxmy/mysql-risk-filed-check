package com.fieldcheck.repository;

import com.fieldcheck.entity.RiskResult;
import com.fieldcheck.entity.RiskStatus;
import com.fieldcheck.entity.RiskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RiskResultRepository extends JpaRepository<RiskResult, Long> {
    
    Page<RiskResult> findByExecutionId(Long executionId, Pageable pageable);
    
    @Query("SELECT r FROM RiskResult r WHERE r.execution.id = :executionId")
    List<RiskResult> findByExecutionId(@Param("executionId") Long executionId);
    
    @Query("SELECT r FROM RiskResult r WHERE r.execution.task.id = :taskId")
    List<RiskResult> findByTaskId(@Param("taskId") Long taskId);
    
    @Query("SELECT r FROM RiskResult r WHERE " +
           "(:executionId IS NULL OR r.execution.id = :executionId) AND " +
           "(:databaseName IS NULL OR r.databaseName LIKE %:databaseName%) AND " +
           "(:tableName IS NULL OR r.tableName LIKE %:tableName%) AND " +
           "(:riskType IS NULL OR r.riskType = :riskType) AND " +
           "(:status IS NULL OR r.status = :status)")
    Page<RiskResult> findByConditions(@Param("executionId") Long executionId,
                                      @Param("databaseName") String databaseName,
                                      @Param("tableName") String tableName,
                                      @Param("riskType") RiskType riskType,
                                      @Param("status") RiskStatus status,
                                      Pageable pageable);
    
    @Query("SELECT r FROM RiskResult r WHERE " +
           "(:executionId IS NULL OR r.execution.id = :executionId) AND " +
           "(:databaseName IS NULL OR r.databaseName LIKE %:databaseName%) AND " +
           "(:tableName IS NULL OR r.tableName LIKE %:tableName%) AND " +
           "(:riskType IS NULL OR r.riskType = :riskType) AND " +
           "(:status IS NULL OR r.status = :status)")
    List<RiskResult> findByConditions(@Param("executionId") Long executionId,
                                      @Param("databaseName") String databaseName,
                                      @Param("tableName") String tableName,
                                      @Param("riskType") RiskType riskType,
                                      @Param("status") RiskStatus status);
    
    @Query("SELECT r.riskType, COUNT(r) FROM RiskResult r WHERE r.execution.id = :executionId GROUP BY r.riskType")
    List<Object[]> countByRiskTypeForExecution(@Param("executionId") Long executionId);
    
    @Query("SELECT COUNT(r) FROM RiskResult r WHERE r.status = :status")
    long countByStatus(@Param("status") RiskStatus status);
    
    @Query("SELECT r FROM RiskResult r WHERE r.createdAt >= :startTime")
    List<RiskResult> findRecentRisks(@Param("startTime") LocalDateTime startTime);
    
    @Query("SELECT COUNT(r) FROM RiskResult r WHERE r.execution.id = :executionId")
    long countByExecutionId(@Param("executionId") Long executionId);
    
    @Query("SELECT DATE(r.createdAt), COUNT(r) FROM RiskResult r WHERE r.createdAt >= :startTime GROUP BY DATE(r.createdAt) ORDER BY DATE(r.createdAt)")
    List<Object[]> getRiskTrend(@Param("startTime") LocalDateTime startTime);
    
    @Query("SELECT r.riskType, COUNT(r) FROM RiskResult r GROUP BY r.riskType")
    List<Object[]> countByRiskType();
}
