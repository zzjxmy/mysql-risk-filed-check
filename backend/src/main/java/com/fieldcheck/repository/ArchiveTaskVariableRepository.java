package com.fieldcheck.repository;

import com.fieldcheck.entity.ArchiveTaskVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArchiveTaskVariableRepository extends JpaRepository<ArchiveTaskVariable, Long> {
}
