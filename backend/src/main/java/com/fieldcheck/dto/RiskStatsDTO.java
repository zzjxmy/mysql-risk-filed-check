package com.fieldcheck.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskStatsDTO {
    private Long totalRisks;
    private Long pendingRisks;
    private Long ignoredRisks;
    private Long resolvedRisks;
    private Map<String, Long> risksByType;
    private List<TrendItem> riskTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendItem {
        private String date;
        private Long count;
    }
}
