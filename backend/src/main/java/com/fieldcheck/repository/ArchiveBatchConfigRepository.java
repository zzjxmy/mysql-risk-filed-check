package com.fieldcheck.repository;

import com.fieldcheck.entity.ArchiveBatchConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArchiveBatchConfigRepository extends JpaRepository<ArchiveBatchConfig, Long> {
}
