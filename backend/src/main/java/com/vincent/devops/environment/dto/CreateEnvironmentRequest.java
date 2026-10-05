package com.vincent.devops.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建环境请求。
 *
 * @param code 环境编码，在项目内唯一，只允许小写字母、数字和短横线
 * @param name 环境名称，最长 64 个字符
 * @param namespace Kubernetes Namespace，最长 63 个字符
 * @param clusterName Kubernetes 集群名称，最长 64 个字符
 * @param description 环境描述，最长 500 个字符
 */
public record CreateEnvironmentRequest(
        @NotBlank(message = "环境编码不能为空")
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$", message = "环境编码只能使用小写字母、数字和短横线")
        String code,

        @NotBlank(message = "环境名称不能为空")
        @Size(max = 64, message = "环境名称不能超过64个字符")
        String name,

        @NotBlank(message = "Namespace 不能为空")
        @Size(max = 63, message = "Namespace 不能超过63个字符")
        @Pattern(regexp = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$", message = "Namespace 必须是小写字母、数字和短横线组成的合法名称")
        String namespace,

        @Size(max = 64, message = "集群名称不能超过64个字符")
        String clusterName,

        @Size(max = 500, message = "环境描述不能超过500个字符")
        String description
) {
}
