package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.dto.TableStatsDTO;
import com.fieldcheck.service.TableStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/table-stats")
@RequiredArgsConstructor
public class TableStatsController {

    private final TableStatsService tableStatsService;

    @GetMapping
    public ApiResponse<List<TableStatsDTO>> getTableStats(
            @RequestParam Long connectionId,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double minFragmentMb) {
        return ApiResponse.success(tableStatsService.getTableStats(connectionId, schema, keyword, minFragmentMb));
    }
}
