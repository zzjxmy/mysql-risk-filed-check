package com.fieldcheck.repository;

import com.fieldcheck.entity.ArchiveTask;
import com.fieldcheck.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArchiveTaskRepository extends JpaRepository<ArchiveTask, Long> {

    @Query("SELECT t FROM ArchiveTask t WHERE " +
            "(:name IS NULL OR t.name LIKE %:name%) AND " +
            "(:status IS NULL OR t.status = :status)")
    Page<ArchiveTask> findByConditions(@Param("name") String name,
                                       @Param("status") TaskStatus status,
                                       Pageable pageable);

    List<ArchiveTask> findByStatusAndCronExpressionIsNotNull(TaskStatus status);

    @Query("SELECT DISTINCT t FROM ArchiveTask t " +
            "LEFT JOIN FETCH t.variables v " +
            "LEFT JOIN FETCH v.connection " +
            "LEFT JOIN FETCH t.steps " +
            "LEFT JOIN FETCH t.batchConfig bc " +
            "LEFT JOIN FETCH bc.queryConnection " +
            "LEFT JOIN FETCH bc.targetConnection " +
            "JOIN FETCH t.sourceConnection " +
            "JOIN FETCH t.destConnection " +
            "WHERE t.id = :id")
    Optional<ArchiveTask> findByIdWithDetails(@Param("id") Long id);
}
