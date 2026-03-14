package com.fieldcheck.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
public class ConnectionDTO {
    private Long id;
    
    @NotBlank(message = "连接名称不能为空")
    private String name;
    
    @NotBlank(message = "主机地址不能为空")
    private String host;
    
    @NotNull(message = "端口不能为空")
    @Min(value = 1, message = "端口范围1-65535")
    @Max(value = 65535, message = "端口范围1-65535")
    private Integer port;
    
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    private String password;
    
    private String remark;
    
    private Boolean enabled;
}
