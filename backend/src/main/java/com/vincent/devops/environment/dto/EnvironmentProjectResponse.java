package com.vincent.devops.environment.dto;

import java.time.LocalDateTime;

/**
 * 面向全局环境列表的响应对象。
 *
 * <p>所有主键统一转换为字符串，避免浏览器处理 64 位主键时发生精度丢失。</p>
 */
public record EnvironmentProjectResponse(
        String id,
        String projectId,
        String projectCode,
        String projectName,
        String code,
        String name,
        String namespace,
        String clusterName,
        String status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EnvironmentProjectResponse from(EnvironmentProjectRow row) {
        return new EnvironmentProjectResponse(
                String.valueOf(row.getId()),
                String.valueOf(row.getProjectId()),
                row.getProjectCode(),
                row.getProjectName(),
                row.getCode(),
                row.getName(),
                row.getNamespace(),
                row.getClusterName(),
                row.getStatus(),
                row.getDescription(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
