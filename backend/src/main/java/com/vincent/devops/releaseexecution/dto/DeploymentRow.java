package com.vincent.devops.releaseexecution.dto;

import java.time.LocalDateTime;

/** 部署记录数据库行。 */
public record DeploymentRow(
        String id, String projectId, String serviceId, String environmentId,
        String pipelineRunId, String imageArtifactId, String previousDeploymentId,
        String operationType, String status, String helmReleaseName, Integer helmRevision,
        LocalDateTime deployedAt, LocalDateTime finishedAt, String failureSummary,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
