package com.vincent.devops.integration.github;

import com.vincent.devops.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GitHub push Webhook 入口；验签通过后才允许创建平台构建记录。 */
@RestController
@RequestMapping("/api/v1/integrations/github")
public class GitHubWebhookController {
    private final GitHubWebhookVerifier verifier;
    private final GitHubWebhookProcessor processor;
    private final String webhookSecret;

    public GitHubWebhookController(GitHubWebhookVerifier verifier, GitHubWebhookProcessor processor,
                                   @Value("${devops.integration.github.webhook-secret:}") String webhookSecret) {
        this.verifier = verifier;
        this.processor = processor;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    public ApiResponse<GitHubWebhookProcessResult> webhook(
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventName,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload
    ) {
        GitHubWebhookEvent event = verifier.verifyAndParse(eventName, deliveryId, signature, payload, webhookSecret);
        return ApiResponse.ok(processor.process(event));
    }
}
