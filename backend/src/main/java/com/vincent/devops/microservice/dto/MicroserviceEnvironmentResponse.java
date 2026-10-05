package com.vincent.devops.microservice.dto;

import java.time.LocalDateTime;

/**
 * 面向微服务全局列表的响应对象。
 *
 * <p>环境字段可为空，表示该服务尚未关联运行环境；所有主键以字符串返回以避免前端精度丢失。</p>
 */
public record MicroserviceEnvironmentResponse(
        String bindingId,
        String projectId,
        String projectCode,
        String projectName,
        String environmentId,
        String environmentCode,
        String environmentName,
        String id,
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

    public static MicroserviceEnvironmentResponse from(MicroserviceEnvironmentRow row) {
        return new MicroserviceEnvironmentResponse(
                asString(row.getBindingId()),
                asString(row.getProjectId()),
                row.getProjectCode(),
                row.getProjectName(),
                asString(row.getEnvironmentId()),
                row.getEnvironmentCode(),
                row.getEnvironmentName(),
                asString(row.getId()),
                row.getServiceCode(),
                row.getServiceName(),
                row.getRepositoryUrl(),
                row.getBranchName(),
                row.getBuildType(),
                row.getImageRepository(),
                row.getPort(),
                row.getReplicas(),
                row.getCpuLimit(),
                row.getMemoryLimit(),
                row.getStatus(),
                row.getDescription(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private static String asString(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
