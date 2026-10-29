package com.vincent.devops.releaseexecution.dto;

import java.time.LocalDateTime;

/** 镜像制品数据库行。 */
public record ImageArtifactRow(
        String id, String projectId, String serviceId, String pipelineRunId,
        String imageRepository, String imageTag, String imageDigest, String status,
        LocalDateTime publishedAt, LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
