package com.vincent.devops.releaseexecution.dto;

/** 镜像制品记录响应。 */
public record ImageArtifactResponse(
        String id,
        String projectId,
        String serviceId,
        String pipelineRunId,
        String imageRepository,
        String imageTag,
        String imageDigest,
        String status,
        String publishedAt,
        String createdAt,
        String updatedAt
) {
}
