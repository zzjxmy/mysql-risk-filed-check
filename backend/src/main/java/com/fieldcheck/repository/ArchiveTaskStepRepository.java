package com.fieldcheck.repository;

import com.fieldcheck.entity.ArchiveTaskStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArchiveTaskStepRepository extends JpaRepository<ArchiveTaskStep, Long> {
}
