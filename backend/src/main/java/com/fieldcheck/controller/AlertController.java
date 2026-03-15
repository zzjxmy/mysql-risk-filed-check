package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.entity.AlertConfig;
import com.fieldcheck.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ApiResponse<List<AlertConfig>> getAllConfigs(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.success(alertService.getAllConfigs(name, type, enabled));
    }

    @GetMapping("/enabled")
    public ApiResponse<List<AlertConfig>> getEnabledConfigs() {
        return ApiResponse.success(alertService.getEnabledConfigs());
    }

    @GetMapping("/{id}")
    public ApiResponse<AlertConfig> getConfig(@PathVariable Long id) {
        return ApiResponse.success(alertService.getConfig(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<AlertConfig> createConfig(@RequestBody AlertConfig config) {
        return ApiResponse.success("创建成功", alertService.createConfig(config));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<AlertConfig> updateConfig(@PathVariable Long id, @RequestBody AlertConfig config) {
        return ApiResponse.success("更新成功", alertService.updateConfig(id, config));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        alertService.deleteConfig(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<Void> testAlert(@PathVariable Long id) {
        alertService.testAlert(id);
        return ApiResponse.success("测试消息已发送", null);
    }
}
