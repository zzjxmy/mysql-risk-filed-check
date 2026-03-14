package com.fieldcheck.dto;

import com.fieldcheck.entity.TaskStatus;
import com.fieldcheck.entity.WhitelistType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Data
public class TaskDTO {
    private Long id;
    
    @NotBlank(message = "任务名称不能为空")
    private String name;
    
    @NotNull(message = "数据库连接不能为空")
    private Long connectionId;
    
    private String connectionName;
    
    private String dbPattern;
    
    private String tablePattern;
    
    private Boolean fullScan;
    
    private Integer sampleSize;
    
    private Long maxTableRows;
    
    private Integer thresholdPct;
    
    private Integer y2038WarningYear;
    
    private WhitelistType whitelistType;
    
    private String customWhitelist;
    
    private String cronExpression;
    
    private TaskStatus status;
    
    private Set<Long> alertConfigIds;
}
