package com.vincent.devops.releaseconfig.dto;

/** Webhook 根据仓库标识匹配到的发布目标。 */
public record WebhookReleaseTarget(
        String repositoryId,
        String projectId,
        String serviceId,
        String environmentId,
        String defaultBranch,
        String sourceRoot,
        String changePathRules,
        String releasePolicy
) {
}
