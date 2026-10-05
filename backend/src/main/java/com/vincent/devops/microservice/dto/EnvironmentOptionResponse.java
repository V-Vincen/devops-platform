package com.vincent.devops.microservice.dto;

import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.project.domain.ProjectEntity;

/**
 * 微服务新增表单可选环境。
 *
 * <p>仅返回启用项目下的启用环境，前端据此限制一次多选只能来自同一个项目。</p>
 */
public record EnvironmentOptionResponse(
        String id,
        String projectId,
        String projectCode,
        String projectName,
        String code,
        String name
) {

    public static EnvironmentOptionResponse from(ProjectEntity project, EnvironmentEntity environment) {
        return new EnvironmentOptionResponse(
                String.valueOf(environment.getId()),
                String.valueOf(project.getId()),
                project.getCode(),
                project.getName(),
                environment.getCode(),
                environment.getName()
        );
    }
}
