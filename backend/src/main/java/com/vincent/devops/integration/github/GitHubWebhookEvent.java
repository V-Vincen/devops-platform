package com.vincent.devops.integration.github;

import java.util.List;

/** GitHub push 事件的内部标准化结果，不保留原始请求和签名。 */
public record GitHubWebhookEvent(
        String deliveryId,
        String repositoryKey,
        String gitRef,
        String commitSha,
        List<String> changedFiles
) {
}
