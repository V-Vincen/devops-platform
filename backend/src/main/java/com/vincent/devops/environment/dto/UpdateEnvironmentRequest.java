package com.vincent.devops.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改环境基本信息请求。
 *
 * <p>环境编码创建后不允许修改。</p>
 *
 * @param name 新的环境名称
 * @param namespace 新的 Kubernetes Namespace
 * @param clusterName Kubernetes 集群名称
 * @param description 环境描述
 */
public record UpdateEnvironmentRequest(
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
