package com.fieldcheck.repository;

import com.fieldcheck.entity.TaskAlertConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskAlertConfigRepository extends JpaRepository<TaskAlertConfig, Long> {

    List<TaskAlertConfig> findByTaskId(Long taskId);

    @org.springframework.data.jpa.repository.Query("SELECT t FROM TaskAlertConfig t JOIN FETCH t.alertConfig WHERE t.task.id = :taskId")
    List<TaskAlertConfig> findByTaskIdWithAlertConfig(@org.springframework.data.repository.query.Param("taskId") Long taskId);

    void deleteByTaskId(Long taskId);

    boolean existsByTaskIdAndAlertConfigId(Long taskId, Long alertConfigId);
}
