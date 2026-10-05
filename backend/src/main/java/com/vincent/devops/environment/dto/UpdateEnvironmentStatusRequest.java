package com.vincent.devops.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 修改环境状态请求。
 *
 * @param status 目标状态，只允许 {@code ACTIVE} 或 {@code DISABLED}
 */
public record UpdateEnvironmentStatusRequest(
        @NotBlank(message = "环境状态不能为空")
        @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "环境状态只能是 ACTIVE 或 DISABLED")
        String status
) {
}
