package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.entity.WhitelistRule;
import com.fieldcheck.service.WhitelistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/whitelist")
@RequiredArgsConstructor
public class WhitelistController {

    private final WhitelistService whitelistService;

    @GetMapping
    public ApiResponse<Page<WhitelistRule>> getWhitelistRules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(whitelistService.getWhitelistRules(pageRequest));
    }

    @GetMapping("/all")
    public ApiResponse<List<WhitelistRule>> getAllEnabledRules() {
        return ApiResponse.success(whitelistService.getGlobalWhitelist());
    }

    @GetMapping("/{id}")
    public ApiResponse<WhitelistRule> getRule(@PathVariable Long id) {
        return ApiResponse.success(whitelistService.getRule(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<WhitelistRule> createRule(@RequestBody WhitelistRule rule) {
        return ApiResponse.success("创建成功", whitelistService.createRule(rule));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<WhitelistRule> updateRule(@PathVariable Long id, @RequestBody WhitelistRule rule) {
        return ApiResponse.success("更新成功", whitelistService.updateRule(id, rule));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        whitelistService.deleteRule(id);
        return ApiResponse.success("删除成功", null);
    }
}
