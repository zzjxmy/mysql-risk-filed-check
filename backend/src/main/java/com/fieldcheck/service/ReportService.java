package com.fieldcheck.service;

import com.fieldcheck.entity.*;
import com.fieldcheck.repository.RiskResultRepository;
import com.fieldcheck.repository.TaskExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final TaskExecutionRepository executionRepository;
    private final RiskResultRepository riskResultRepository;

    private static final String REPORT_DIR = "reports";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 生成任务执行报告
     */
    @Transactional(readOnly = true)
    public String generateExecutionReport(Long executionId) throws IOException {
        TaskExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new RuntimeException("执行记录不存在"));

        List<RiskResult> risks = riskResultRepository.findByExecutionId(executionId);
        
        StringBuilder report = new StringBuilder();
        report.append(generateReportHeader(execution));
        report.append(generateSummarySection(execution, risks));
        report.append(generateRiskDetailsSection(risks));
        report.append(generateRecommendationsSection(risks));
        report.append(generateReportFooter());

        // 保存报告文件
        String fileName = String.format("report_%d_%s.md", executionId, 
                LocalDateTime.now().format(FILE_DATE_FORMAT));
        Path reportPath = saveReport(fileName, report.toString());
        
        return reportPath.toString();
    }

    /**
     * 生成任务报告（按任务ID，包含所有执行记录）
     */
    @Transactional(readOnly = true)
    public String generateTaskReport(Long taskId) throws IOException {
        List<TaskExecution> executions = executionRepository.findByTaskIdOrderByStartTimeDesc(taskId);
        if (executions.isEmpty()) {
            throw new RuntimeException("没有找到执行记录");
        }

        TaskExecution latestExecution = executions.get(0);
        List<RiskResult> risks = riskResultRepository.findByTaskId(taskId);

        StringBuilder report = new StringBuilder();
        report.append("# 任务检查报告\n\n");
        report.append("**任务名称:** ").append(latestExecution.getTask().getName()).append("\n\n");
        report.append("**报告生成时间:** ").append(LocalDateTime.now().format(DATE_FORMAT)).append("\n\n");
        report.append("---\n\n");

        // 执行历史摘要
        report.append("## 执行历史\n\n");
        report.append("| 执行ID | 开始时间 | 结束时间 | 状态 | 发现风险数 |\n");
        report.append("|--------|----------|----------|------|------------|\n");
        for (TaskExecution exec : executions) {
            long riskCount = riskResultRepository.countByExecutionId(exec.getId());
            report.append(String.format("| %d | %s | %s | %s | %d |\n",
                    exec.getId(),
                    exec.getStartTime() != null ? exec.getStartTime().format(DATE_FORMAT) : "-",
                    exec.getEndTime() != null ? exec.getEndTime().format(DATE_FORMAT) : "-",
                    exec.getStatus().name(),
                    riskCount));
        }
        report.append("\n");

        // 风险汇总
        report.append(generateRiskSummaryByType(risks));
        report.append(generateRiskDetailsSection(risks));
        report.append(generateRecommendationsSection(risks));
        report.append(generateReportFooter());

        String fileName = String.format("task_report_%d_%s.md", taskId,
                LocalDateTime.now().format(FILE_DATE_FORMAT));
        Path reportPath = saveReport(fileName, report.toString());

        return reportPath.toString();
    }

    /**
     * 获取报告文件内容
     */
    public byte[] getReportContent(String fileName) throws IOException {
        Path reportPath = Paths.get(REPORT_DIR, fileName);
        if (!Files.exists(reportPath)) {
            throw new FileNotFoundException("报告文件不存在: " + fileName);
        }
        return Files.readAllBytes(reportPath);
    }

    /**
     * 列出所有报告文件
     */
    public List<Map<String, Object>> listReports() throws IOException {
        Path reportDir = Paths.get(REPORT_DIR);
        if (!Files.exists(reportDir)) {
            return Collections.emptyList();
        }

        return Files.list(reportDir)
                .filter(path -> path.toString().endsWith(".md"))
                .map(path -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("fileName", path.getFileName().toString());
                    try {
                        info.put("size", Files.size(path));
                        info.put("createdAt", Files.getLastModifiedTime(path).toInstant().toString());
                    } catch (IOException e) {
                        info.put("size", 0);
                    }
                    return info;
                })
                .sorted((a, b) -> ((String) b.get("createdAt")).compareTo((String) a.get("createdAt")))
                .collect(Collectors.toList());
    }

    /**
     * 删除报告文件
     */
    public void deleteReport(String fileName) throws IOException {
        Path reportPath = Paths.get(REPORT_DIR, fileName);
        if (Files.exists(reportPath)) {
            Files.delete(reportPath);
        }
    }

    private String generateReportHeader(TaskExecution execution) {
        StringBuilder header = new StringBuilder();
        header.append("# MySQL 字段容量风险检查报告\n\n");
        header.append("---\n\n");
        header.append("## 基本信息\n\n");
        header.append("| 项目 | 内容 |\n");
        header.append("|------|------|\n");
        header.append(String.format("| **任务名称** | %s |\n", execution.getTask().getName()));
        header.append(String.format("| **执行ID** | %d |\n", execution.getId()));
        header.append(String.format("| **开始时间** | %s |\n", 
                execution.getStartTime() != null ? execution.getStartTime().format(DATE_FORMAT) : "-"));
        header.append(String.format("| **结束时间** | %s |\n",
                execution.getEndTime() != null ? execution.getEndTime().format(DATE_FORMAT) : "-"));
        header.append(String.format("| **执行状态** | %s |\n", execution.getStatus().name()));
        int progress = 0;
        if (execution.getTotalTables() != null && execution.getTotalTables() > 0) {
            progress = (execution.getProcessedTables() * 100) / execution.getTotalTables();
        }
        header.append(String.format("| **检查进度** | %d%% |\n", progress));
        header.append("\n");
        return header.toString();
    }

    private String generateSummarySection(TaskExecution execution, List<RiskResult> risks) {
        StringBuilder summary = new StringBuilder();
        summary.append("## 检查摘要\n\n");

        // 按风险类型统计
        Map<RiskType, Long> riskByType = risks.stream()
                .collect(Collectors.groupingBy(RiskResult::getRiskType, Collectors.counting()));

        // 按状态统计
        Map<RiskStatus, Long> riskByStatus = risks.stream()
                .collect(Collectors.groupingBy(RiskResult::getStatus, Collectors.counting()));

        summary.append("### 风险统计\n\n");
        summary.append("| 风险类型 | 数量 |\n");
        summary.append("|----------|------|\n");
        for (RiskType type : RiskType.values()) {
            long count = riskByType.getOrDefault(type, 0L);
            summary.append(String.format("| %s | %d |\n", getRiskTypeName(type), count));
        }
        summary.append(String.format("| **总计** | **%d** |\n", risks.size()));
        summary.append("\n");

        summary.append("### 状态分布\n\n");
        summary.append("| 状态 | 数量 |\n");
        summary.append("|------|------|\n");
        for (RiskStatus status : RiskStatus.values()) {
            long count = riskByStatus.getOrDefault(status, 0L);
            summary.append(String.format("| %s | %d |\n", getStatusName(status), count));
        }
        summary.append("\n");

        return summary.toString();
    }

    private String generateRiskSummaryByType(List<RiskResult> risks) {
        StringBuilder summary = new StringBuilder();
        summary.append("## 风险汇总\n\n");

        Map<RiskType, Long> riskByType = risks.stream()
                .collect(Collectors.groupingBy(RiskResult::getRiskType, Collectors.counting()));

        summary.append("| 风险类型 | 数量 | 占比 |\n");
        summary.append("|----------|------|------|\n");
        int total = risks.size();
        for (RiskType type : RiskType.values()) {
            long count = riskByType.getOrDefault(type, 0L);
            double ratio = total > 0 ? (count * 100.0 / total) : 0;
            summary.append(String.format("| %s | %d | %.1f%% |\n", getRiskTypeName(type), count, ratio));
        }
        summary.append("\n");

        return summary.toString();
    }

    private String generateRiskDetailsSection(List<RiskResult> risks) {
        if (risks.isEmpty()) {
            return "## 风险详情\n\n暂无发现风险。\n\n";
        }

        StringBuilder details = new StringBuilder();
        details.append("## 风险详情\n\n");

        // 按风险类型分组
        Map<RiskType, List<RiskResult>> groupedRisks = risks.stream()
                .collect(Collectors.groupingBy(RiskResult::getRiskType));

        for (RiskType type : RiskType.values()) {
            List<RiskResult> typeRisks = groupedRisks.get(type);
            if (typeRisks == null || typeRisks.isEmpty()) continue;

            details.append(String.format("### %s (%d项)\n\n", getRiskTypeName(type), typeRisks.size()));
            details.append("| 数据库 | 表 | 字段 | 字段类型 | 当前值 | 阈值 | 使用率 | 状态 |\n");
            details.append("|--------|----|----- |----------|--------|------|--------|------|\n");

            for (RiskResult risk : typeRisks) {
                details.append(String.format("| %s | %s | %s | %s | %s | %s | %.2f%% | %s |\n",
                        risk.getDatabaseName(),
                        risk.getTableName(),
                        risk.getColumnName(),
                        risk.getColumnType(),
                        risk.getCurrentValue() != null ? risk.getCurrentValue() : "-",
                        risk.getThresholdValue() != null ? risk.getThresholdValue() : "-",
                        risk.getUsagePercent() != null ? risk.getUsagePercent().doubleValue() : 0,
                        getStatusName(risk.getStatus())));
            }
            details.append("\n");
        }

        return details.toString();
    }

    private String generateRecommendationsSection(List<RiskResult> risks) {
        StringBuilder recommendations = new StringBuilder();
        recommendations.append("## 优化建议\n\n");

        if (risks.isEmpty()) {
            recommendations.append("当前没有发现需要处理的风险。\n\n");
            return recommendations.toString();
        }

        // 按风险类型给出建议
        Map<RiskType, List<RiskResult>> groupedRisks = risks.stream()
                .filter(r -> r.getStatus() == RiskStatus.PENDING)
                .collect(Collectors.groupingBy(RiskResult::getRiskType));

        int suggestionIndex = 1;

        if (groupedRisks.containsKey(RiskType.INT_OVERFLOW)) {
            recommendations.append(String.format("### %d. 整型溢出风险处理\n\n", suggestionIndex++));
            recommendations.append("以下字段存在整型溢出风险，建议扩展字段类型：\n\n");
            recommendations.append("```sql\n");
            for (RiskResult risk : groupedRisks.get(RiskType.INT_OVERFLOW)) {
                String newType = suggestNewIntType(risk.getColumnType());
                recommendations.append(String.format("ALTER TABLE `%s`.`%s` MODIFY COLUMN `%s` %s;\n",
                        risk.getDatabaseName(), risk.getTableName(), risk.getColumnName(), newType));
            }
            recommendations.append("```\n\n");
        }

        if (groupedRisks.containsKey(RiskType.Y2038)) {
            recommendations.append(String.format("### %d. Y2038 问题处理\n\n", suggestionIndex++));
            recommendations.append("以下字段存在 Y2038 时间戳问题，建议迁移到 DATETIME 或 BIGINT：\n\n");
            recommendations.append("```sql\n");
            for (RiskResult risk : groupedRisks.get(RiskType.Y2038)) {
                recommendations.append(String.format("ALTER TABLE `%s`.`%s` MODIFY COLUMN `%s` DATETIME;\n",
                        risk.getDatabaseName(), risk.getTableName(), risk.getColumnName()));
            }
            recommendations.append("```\n\n");
            recommendations.append("> **注意**: 迁移前请确保应用程序已做好兼容性处理。\n\n");
        }

        if (groupedRisks.containsKey(RiskType.DECIMAL_OVERFLOW)) {
            recommendations.append(String.format("### %d. 小数溢出风险处理\n\n", suggestionIndex++));
            recommendations.append("以下字段存在小数溢出风险，建议扩展精度：\n\n");
            for (RiskResult risk : groupedRisks.get(RiskType.DECIMAL_OVERFLOW)) {
                recommendations.append(String.format("- `%s`.`%s`.`%s`: 当前类型 %s，建议扩展精度\n",
                        risk.getDatabaseName(), risk.getTableName(), risk.getColumnName(), risk.getColumnType()));
            }
            recommendations.append("\n");
        }

        recommendations.append("---\n\n");
        recommendations.append("> **执行建议**: \n");
        recommendations.append("> 1. 在低峰期执行 DDL 变更\n");
        recommendations.append("> 2. 建议使用 pt-online-schema-change 或 gh-ost 进行在线变更\n");
        recommendations.append("> 3. 变更前做好数据备份\n");
        recommendations.append("> 4. 变更后验证应用程序兼容性\n\n");

        return recommendations.toString();
    }

    private String generateReportFooter() {
        return String.format("\n---\n\n*报告生成时间: %s*\n\n*由 MySQL 字段容量风险检查平台自动生成*\n",
                LocalDateTime.now().format(DATE_FORMAT));
    }

    private Path saveReport(String fileName, String content) throws IOException {
        Path reportDir = Paths.get(REPORT_DIR);
        if (!Files.exists(reportDir)) {
            Files.createDirectories(reportDir);
        }

        Path reportPath = reportDir.resolve(fileName);
        Files.write(reportPath, content.getBytes(StandardCharsets.UTF_8));
        log.info("Report saved to: {}", reportPath);
        return reportPath;
    }

    private String getRiskTypeName(RiskType type) {
        switch (type) {
            case INT_OVERFLOW: return "整型溢出";
            case Y2038: return "Y2038问题";
            case DECIMAL_OVERFLOW: return "小数溢出";
            default: return type.name();
        }
    }

    private String getStatusName(RiskStatus status) {
        switch (status) {
            case PENDING: return "待处理";
            case IGNORED: return "已忽略";
            case RESOLVED: return "已解决";
            default: return status.name();
        }
    }

    private String suggestNewIntType(String currentType) {
        if (currentType == null) return "BIGINT";
        String type = currentType.toUpperCase();
        if (type.contains("TINYINT")) return "SMALLINT";
        if (type.contains("SMALLINT")) return "MEDIUMINT";
        if (type.contains("MEDIUMINT")) return "INT";
        if (type.contains("INT")) return "BIGINT";
        return "BIGINT";
    }
}
