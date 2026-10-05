package com.vincent.devops.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建项目请求。
 *
 * @param code 项目编码，只允许小写字母、数字和短横线，长度 2~32
 * @param name 项目名称，最长 64 个字符
 * @param description 项目描述，最长 500 个字符
 */
public record CreateProjectRequest(
        @NotBlank(message = "项目编码不能为空")
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$", message = "项目编码只能使用小写字母、数字和短横线")
        String code,

        @NotBlank(message = "项目名称不能为空")
        @Size(max = 64, message = "项目名称不能超过64个字符")
        String name,

        @Size(max = 500, message = "项目描述不能超过500个字符")
        String description
) {
}
