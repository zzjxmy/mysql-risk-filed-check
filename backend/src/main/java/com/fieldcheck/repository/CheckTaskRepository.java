package com.fieldcheck.repository;

import com.fieldcheck.entity.CheckTask;
import com.fieldcheck.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheckTaskRepository extends JpaRepository<CheckTask, Long> {
    
    @Query("SELECT t FROM CheckTask t WHERE " +
           "(:name IS NULL OR t.name LIKE %:name%) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:connectionId IS NULL OR t.connection.id = :connectionId)")
    Page<CheckTask> findByConditions(@Param("name") String name,
                                     @Param("status") TaskStatus status,
                                     @Param("connectionId") Long connectionId,
                                     Pageable pageable);
    
    List<CheckTask> findByStatusAndCronExpressionIsNotNull(TaskStatus status);
    
    List<CheckTask> findByConnectionId(Long connectionId);
}
