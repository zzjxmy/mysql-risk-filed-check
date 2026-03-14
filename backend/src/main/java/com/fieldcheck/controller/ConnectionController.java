package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.dto.ConnectionDTO;
import com.fieldcheck.entity.DbConnection;
import com.fieldcheck.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @GetMapping
    public ApiResponse<Page<ConnectionDTO>> getConnections(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DbConnection> connections = connectionService.getConnections(name, enabled, pageRequest);
        Page<ConnectionDTO> dtoPage = connections.map(connectionService::toDTO);
        return ApiResponse.success(dtoPage);
    }

    @GetMapping("/{id}")
    public ApiResponse<ConnectionDTO> getConnection(@PathVariable Long id) {
        DbConnection connection = connectionService.getConnection(id);
        return ApiResponse.success(connectionService.toDTO(connection));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<ConnectionDTO> createConnection(
            @Valid @RequestBody ConnectionDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        DbConnection connection = connectionService.createConnection(dto, userDetails.getUsername());
        return ApiResponse.success("创建成功", connectionService.toDTO(connection));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<ConnectionDTO> updateConnection(
            @PathVariable Long id,
            @Valid @RequestBody ConnectionDTO dto) {
        DbConnection connection = connectionService.updateConnection(id, dto);
        return ApiResponse.success("更新成功", connectionService.toDTO(connection));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteConnection(@PathVariable Long id) {
        connectionService.deleteConnection(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/test")
    public ApiResponse<Boolean> testConnection(@RequestBody ConnectionDTO dto) {
        try {
            boolean result = connectionService.testConnection(dto);
            return ApiResponse.success("连接成功", result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
