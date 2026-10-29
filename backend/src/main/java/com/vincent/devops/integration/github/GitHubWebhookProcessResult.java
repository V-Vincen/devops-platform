package com.vincent.devops.integration.github;

import java.util.List;

/** GitHub Webhook 验签和平台处理结果。 */
public record GitHubWebhookProcessResult(String deliveryId, List<String> pipelineRunIds, String result) {
}
