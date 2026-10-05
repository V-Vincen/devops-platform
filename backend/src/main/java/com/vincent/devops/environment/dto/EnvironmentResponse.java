package com.vincent.devops.environment.dto;

import com.vincent.devops.environment.domain.EnvironmentEntity;

import java.time.LocalDateTime;

/**
 * 面向前端的环境响应对象。
 *
 * <p>ID 使用字符串返回，避免前端处理 64 位主键时发生精度丢失。</p>
 *
 * @param id 环境 ID
 * @param projectId 所属项目 ID
 * @param code 环境编码
 * @param name 环境名称
 * @param namespace Kubernetes Namespace
 * @param clusterName Kubernetes 集群名称
 * @param status 环境状态
 * @param description 环境描述
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record EnvironmentResponse(
        String id,
        String projectId,
        String code,
        String name,
        String namespace,
        String clusterName,
        String status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EnvironmentResponse from(EnvironmentEntity entity) {
        return new EnvironmentResponse(
                String.valueOf(entity.getId()),
                String.valueOf(entity.getProjectId()),
                entity.getCode(),
                entity.getName(),
                entity.getNamespace(),
                entity.getClusterName(),
                entity.getStatus(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
