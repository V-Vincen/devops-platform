package com.vincent.devops.releaseexecution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建部署或回滚记录；环境从构建记录继承，避免跨环境误部署。 */
public record CreateDeploymentRequest(
        @NotBlank(message = "镜像制品 ID 不能为空")
        String imageArtifactId,

        @NotBlank(message = "操作类型不能为空")
        @Size(max = 20, message = "操作类型不正确")
        String operationType,

        String previousDeploymentId,

        @Size(max = 128, message = "Helm Release 名称不能超过128个字符")
        String helmReleaseName
) {
}
