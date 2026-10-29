package com.vincent.devops.integration.jenkins;

/** Jenkins 回调标准化结果；状态值只允许映射到平台状态。 */
public record JenkinsCallbackEvent(
        Long projectId,
        Long serviceId,
        Long pipelineRunId,
        String status,
        String jobName,
        Long buildNumber,
        String buildUrl,
        String failureSummary,
        String imageRepository,
        String imageTag,
        String imageDigest
) {

    public boolean containsImageArtifact() {
        return imageRepository != null || imageTag != null || imageDigest != null;
    }
}
