package com.fieldcheck.repository;

import com.fieldcheck.entity.ExecutionStatus;
import com.fieldcheck.entity.TaskExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {
    
    Page<TaskExecution> findByTaskId(Long taskId, Pageable pageable);
    
    List<TaskExecution> findByStatus(ExecutionStatus status);
    
    Optional<TaskExecution> findFirstByTaskIdOrderByCreatedAtDesc(Long taskId);
    
    @Query("SELECT e FROM TaskExecution e WHERE e.task.id = :taskId ORDER BY e.startTime DESC")
    List<TaskExecution> findByTaskIdOrderByStartTimeDesc(@Param("taskId") Long taskId);
    
    @Query("SELECT e FROM TaskExecution e WHERE e.task.id = :taskId AND e.status = :status")
    List<TaskExecution> findByTaskIdAndStatus(@Param("taskId") Long taskId, 
                                              @Param("status") ExecutionStatus status);
    
    @Query("SELECT COUNT(e) FROM TaskExecution e WHERE e.status = :status")
    long countByStatus(@Param("status") ExecutionStatus status);
    
    @Query("SELECT e FROM TaskExecution e WHERE e.startTime >= :startTime")
    List<TaskExecution> findRecentExecutions(@Param("startTime") LocalDateTime startTime);
}
