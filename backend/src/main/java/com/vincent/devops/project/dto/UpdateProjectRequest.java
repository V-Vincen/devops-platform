package com.vincent.devops.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改项目基本信息请求。
 *
 * <p>项目编码是项目的稳定标识，创建后不允许修改。</p>
 *
 * @param name 新的项目名称，最长 64 个字符
 * @param description 新的项目描述，最长 500 个字符
 */
public record UpdateProjectRequest(
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 64, message = "项目名称不能超过64个字符")
        String name,

        @Size(max = 500, message = "项目描述不能超过500个字符")
        String description
) {
}
