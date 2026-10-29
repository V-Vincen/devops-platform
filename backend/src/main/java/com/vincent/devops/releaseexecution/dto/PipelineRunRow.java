package com.vincent.devops.releaseexecution.dto;

import java.time.LocalDateTime;

/** 构建记录数据库行。ID 在 SQL 中转成字符串，避免 JSON 长整型精度丢失。 */
public record PipelineRunRow(
        String id, String projectId, String serviceId, String environmentId,
        String repositoryId, String buildSpecId, String triggerType, String externalEventId,
        String gitRef, String commitSha, String status, String jenkinsJobName,
        Long jenkinsBuildNumber, String jenkinsBuildUrl, String parameterSnapshot,
        LocalDateTime queuedAt, LocalDateTime startedAt, LocalDateTime finishedAt,
        String failureSummary, LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
