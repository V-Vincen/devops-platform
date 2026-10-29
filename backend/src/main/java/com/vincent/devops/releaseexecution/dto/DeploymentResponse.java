package com.vincent.devops.releaseexecution.dto;

/** 部署或回滚记录响应。 */
public record DeploymentResponse(
        String id,
        String projectId,
        String serviceId,
        String environmentId,
        String pipelineRunId,
        String imageArtifactId,
        String previousDeploymentId,
        String operationType,
        String status,
        String helmReleaseName,
        Integer helmRevision,
        String deployedAt,
        String finishedAt,
        String failureSummary,
        String createdAt,
        String updatedAt
) {
}
