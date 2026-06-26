package com.fieldcheck.repository;

import com.fieldcheck.entity.ArchiveTaskAlertConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArchiveTaskAlertConfigRepository extends JpaRepository<ArchiveTaskAlertConfig, Long> {

    List<ArchiveTaskAlertConfig> findByTaskId(Long taskId);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM ArchiveTaskAlertConfig a JOIN FETCH a.alertConfig WHERE a.task.id = :taskId")
    List<ArchiveTaskAlertConfig> findByTaskIdWithAlertConfig(@org.springframework.data.repository.query.Param("taskId") Long taskId);

    void deleteByTaskId(Long taskId);
}
