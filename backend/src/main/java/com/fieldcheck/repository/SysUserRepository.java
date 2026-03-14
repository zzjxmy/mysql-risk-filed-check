package com.fieldcheck.repository;

import com.fieldcheck.entity.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsername(String username);
    boolean existsByUsername(String username);
    Page<SysUser> findByUsernameContaining(String username, Pageable pageable);
    Page<SysUser> findByRole(String role, Pageable pageable);
    Page<SysUser> findByEnabled(Boolean enabled, Pageable pageable);
}
