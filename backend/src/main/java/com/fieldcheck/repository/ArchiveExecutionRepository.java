package com.fieldcheck.repository;

import com.fieldcheck.entity.ArchiveExecution;
import com.fieldcheck.entity.ExecutionStatus;
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
public interface ArchiveExecutionRepository extends JpaRepository<ArchiveExecution, Long> {

    Page<ArchiveExecution> findByTaskId(Long taskId, Pageable pageable);

    @Query("SELECT e FROM ArchiveExecution e WHERE e.task.id = :taskId AND e.status = :status")
    List<ArchiveExecution> findByTaskIdAndStatus(@Param("taskId") Long taskId,
                                                 @Param("status") ExecutionStatus status);

    @Query("SELECT e FROM ArchiveExecution e JOIN e.task t WHERE " +
            "(:taskName IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :taskName, '%'))) AND " +
            "(:status IS NULL OR e.status = :status) AND " +
            "(:triggerType IS NULL OR e.triggerType = :triggerType) AND " +
            "(:startFrom IS NULL OR e.startTime >= :startFrom) AND " +
            "(:startTo IS NULL OR e.startTime <= :startTo)")
    Page<ArchiveExecution> findByConditions(@Param("taskName") String taskName,
                                            @Param("status") ExecutionStatus status,
                                            @Param("triggerType") String triggerType,
                                            @Param("startFrom") LocalDateTime startFrom,
                                            @Param("startTo") LocalDateTime startTo,
                                            Pageable pageable);

    @Query("SELECT e FROM ArchiveExecution e JOIN FETCH e.task WHERE e.id = :id")
    Optional<ArchiveExecution> findByIdWithTask(@Param("id") Long id);
}
