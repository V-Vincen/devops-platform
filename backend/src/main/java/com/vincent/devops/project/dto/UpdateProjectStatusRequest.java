package com.vincent.devops.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 修改项目状态请求。
 *
 * @param status 目标状态，只允许 {@code ACTIVE} 或 {@code DISABLED}
 */
public record UpdateProjectStatusRequest(
        @NotBlank(message = "项目状态不能为空")
        @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "项目状态只能是 ACTIVE 或 DISABLED")
        String status
) {
}
