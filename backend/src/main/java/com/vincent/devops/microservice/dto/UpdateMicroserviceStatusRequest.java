package com.vincent.devops.microservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 修改微服务状态请求。
 */
public record UpdateMicroserviceStatusRequest(
        @NotBlank(message = "微服务状态不能为空")
        @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "微服务状态只能是 ACTIVE 或 DISABLED")
        String status
) {
}
