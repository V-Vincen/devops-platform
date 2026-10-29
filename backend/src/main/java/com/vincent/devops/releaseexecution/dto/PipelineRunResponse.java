package com.vincent.devops.releaseexecution.dto;

/** 构建执行记录响应；长整型 ID 以字符串返回，避免前端精度丢失。 */
public record PipelineRunResponse(
        String id,
        String projectId,
        String serviceId,
        String environmentId,
        String repositoryId,
        String buildSpecId,
        String triggerType,
        String externalEventId,
        String gitRef,
        String commitSha,
        String status,
        String jenkinsJobName,
        Long jenkinsBuildNumber,
        String jenkinsBuildUrl,
        String parameterSnapshot,
        String queuedAt,
        String startedAt,
        String finishedAt,
        String failureSummary,
        String createdAt,
        String updatedAt
) {
}
