package com.fieldcheck.repository;

import com.fieldcheck.entity.ArchiveExecution;
import com.fieldcheck.entity.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArchiveExecutionRepository extends JpaRepository<ArchiveExecution, Long> {

    Page<ArchiveExecution> findByTaskId(Long taskId, Pageable pageable);

    @Query("SELECT e FROM ArchiveExecution e WHERE e.task.id = :taskId AND e.status = :status")
    List<ArchiveExecution> findByTaskIdAndStatus(@Param("taskId") Long taskId,
                                                 @Param("status") ExecutionStatus status);

    @Query("SELECT e FROM ArchiveExecution e JOIN FETCH e.task WHERE e.id = :id")
    Optional<ArchiveExecution> findByIdWithTask(@Param("id") Long id);
}
