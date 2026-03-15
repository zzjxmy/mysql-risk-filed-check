package com.fieldcheck.service;

import com.fieldcheck.dto.RiskResultDTO;
import com.fieldcheck.dto.RiskStatsDTO;
import com.fieldcheck.entity.RiskResult;
import com.fieldcheck.entity.RiskStatus;
import com.fieldcheck.entity.RiskType;
import com.fieldcheck.repository.RiskResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskResultService {

    private final RiskResultRepository riskResultRepository;

    public Page<RiskResult> getRiskResults(Long executionId, String databaseName, String tableName, 
                                           RiskType riskType, RiskStatus status, Pageable pageable) {
        return riskResultRepository.findByConditions(executionId, databaseName, tableName, riskType, status, pageable);
    }

    public RiskResult getRiskResult(Long id) {
        return riskResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("风险结果不存在"));
    }

    @Transactional
    public RiskResult updateRiskStatus(Long id, RiskStatus status, String remark) {
        RiskResult result = getRiskResult(id);
        result.setStatus(status);
        if (remark != null) {
            result.setRemark(remark);
        }
        return riskResultRepository.save(result);
    }

    public RiskStatsDTO getStats() {
        long total = riskResultRepository.count();
        long pending = riskResultRepository.countByStatus(RiskStatus.PENDING);
        long ignored = riskResultRepository.countByStatus(RiskStatus.IGNORED);
        long resolved = riskResultRepository.countByStatus(RiskStatus.RESOLVED);

        // Get risk distribution by type
        Map<String, Long> risksByType = new HashMap<>();
        for (RiskType type : RiskType.values()) {
            risksByType.put(type.name(), 0L);
        }
        
        // Query actual counts from database
        List<Object[]> typeCounts = riskResultRepository.countByRiskType();
        for (Object[] row : typeCounts) {
            RiskType type = (RiskType) row[0];
            Long count = ((Number) row[1]).longValue();
            risksByType.put(type.name(), count);
        }
        
        // Get recent risks for trend
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Object[]> trendData = riskResultRepository.getRiskTrend(thirtyDaysAgo);
        List<RiskStatsDTO.TrendItem> trend = trendData.stream()
                .map(row -> RiskStatsDTO.TrendItem.builder()
                        .date(row[0].toString())
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        return RiskStatsDTO.builder()
                .totalRisks(total)
                .pendingRisks(pending)
                .ignoredRisks(ignored)
                .resolvedRisks(resolved)
                .risksByType(risksByType)
                .riskTrend(trend)
                .build();
    }

    public RiskResultDTO toDTO(RiskResult result) {
        return RiskResultDTO.builder()
                .id(result.getId())
                .executionId(result.getExecution().getId())
                .databaseName(result.getDatabaseName())
                .tableName(result.getTableName())
                .columnName(result.getColumnName())
                .columnType(result.getColumnType())
                .riskType(result.getRiskType())
                .riskTypeDesc(getRiskTypeDesc(result.getRiskType()))
                .currentValue(result.getCurrentValue())
                .thresholdValue(result.getThresholdValue())
                .usagePercent(result.getUsagePercent())
                .detail(result.getDetail())
                .suggestion(result.getSuggestion())
                .status(result.getStatus())
                .remark(result.getRemark())
                .createdAt(result.getCreatedAt())
                .build();
    }

    private String getRiskTypeDesc(RiskType type) {
        switch (type) {
            case INT_OVERFLOW: return "整型溢出";
            case DECIMAL_OVERFLOW: return "小数溢出";
            case Y2038: return "Y2038问题";
            case STRING_TRUNCATION: return "字符串截断";
            case DATE_ANOMALY: return "日期异常";
            default: return "其他风险";
        }
    }
}
