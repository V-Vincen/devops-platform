package com.vincent.devops.microservice.dto;

import com.vincent.devops.microservice.domain.MicroserviceEntity;

import java.time.LocalDateTime;

/**
 * 面向前端的微服务响应对象。
 *
 * <p>ID 使用字符串，避免前端处理 64 位主键时发生精度丢失。</p>
 */
public record MicroserviceResponse(
        String id,
        String projectId,
        String serviceCode,
        String serviceName,
        String repositoryUrl,
        String branchName,
        String buildType,
        String imageRepository,
        Integer port,
        Integer replicas,
        String cpuLimit,
        String memoryLimit,
        String status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MicroserviceResponse from(MicroserviceEntity entity) {
        return new MicroserviceResponse(
                String.valueOf(entity.getId()),
                String.valueOf(entity.getProjectId()),
                entity.getServiceCode(),
                entity.getServiceName(),
                entity.getRepositoryUrl(),
                entity.getBranchName(),
                entity.getBuildType(),
                entity.getImageRepository(),
                entity.getPort(),
                entity.getReplicas(),
                entity.getCpuLimit(),
                entity.getMemoryLimit(),
                entity.getStatus(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
