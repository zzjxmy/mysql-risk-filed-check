package com.fieldcheck.service;

import com.fieldcheck.dto.ConnectionDTO;
import com.fieldcheck.entity.DbConnection;
import com.fieldcheck.entity.SysUser;
import com.fieldcheck.repository.DbConnectionRepository;
import com.fieldcheck.repository.SysUserRepository;
import com.fieldcheck.util.AESUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final DbConnectionRepository connectionRepository;
    private final SysUserRepository userRepository;

    @Value("${encryption.aes-key}")
    private String aesKey;

    public Page<DbConnection> getConnections(String name, Boolean enabled, Pageable pageable) {
        return connectionRepository.findByConditions(name, enabled, pageable);
    }

    public DbConnection getConnection(Long id) {
        return connectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("数据库连接不存在"));
    }

    @Transactional
    public DbConnection createConnection(ConnectionDTO dto, String username) {
        if (connectionRepository.existsByName(dto.getName())) {
            throw new RuntimeException("连接名称已存在");
        }

        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        DbConnection connection = DbConnection.builder()
                .name(dto.getName())
                .host(dto.getHost())
                .port(dto.getPort())
                .username(dto.getUsername())
                .password(AESUtil.encrypt(dto.getPassword(), aesKey))
                .remark(dto.getRemark())
                .enabled(dto.getEnabled() != null ? dto.getEnabled() : true)
                .createdBy(user)
                .build();

        return connectionRepository.save(connection);
    }

    @Transactional
    public DbConnection updateConnection(Long id, ConnectionDTO dto) {
        DbConnection connection = getConnection(id);
        
        connection.setName(dto.getName());
        connection.setHost(dto.getHost());
        connection.setPort(dto.getPort());
        connection.setUsername(dto.getUsername());
        
        if (StringUtils.hasText(dto.getPassword())) {
            connection.setPassword(AESUtil.encrypt(dto.getPassword(), aesKey));
        }
        
        connection.setRemark(dto.getRemark());
        if (dto.getEnabled() != null) {
            connection.setEnabled(dto.getEnabled());
        }

        return connectionRepository.save(connection);
    }

    @Transactional
    public void deleteConnection(Long id) {
        DbConnection connection = getConnection(id);
        connectionRepository.delete(connection);
    }

    public boolean testConnection(ConnectionDTO dto) {
        String password = dto.getPassword();
        if (dto.getId() != null && !StringUtils.hasText(password)) {
            DbConnection existing = getConnection(dto.getId());
            password = AESUtil.decrypt(existing.getPassword(), aesKey);
        }

        String url = String.format("jdbc:mysql://%s:%d?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=5000",
                dto.getHost(), dto.getPort());

        try (Connection conn = DriverManager.getConnection(url, dto.getUsername(), password)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("Connection test failed: {}", e.getMessage());
            throw new RuntimeException("连接测试失败: " + e.getMessage());
        }
    }

    public String getDecryptedPassword(Long connectionId) {
        DbConnection connection = getConnection(connectionId);
        return AESUtil.decrypt(connection.getPassword(), aesKey);
    }

    public ConnectionDTO toDTO(DbConnection connection) {
        ConnectionDTO dto = new ConnectionDTO();
        dto.setId(connection.getId());
        dto.setName(connection.getName());
        dto.setHost(connection.getHost());
        dto.setPort(connection.getPort());
        dto.setUsername(connection.getUsername());
        dto.setRemark(connection.getRemark());
        dto.setEnabled(connection.getEnabled());
        return dto;
    }
}
