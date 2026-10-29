package com.vincent.devops.integration.jenkins;

import com.vincent.devops.common.ApiResponse;
import com.vincent.devops.releaseexecution.dto.CreateImageArtifactRequest;
import com.vincent.devops.releaseexecution.dto.UpdatePipelineRunStatusRequest;
import com.vincent.devops.releaseexecution.dto.ImageArtifactResponse;
import com.vincent.devops.releaseexecution.service.ReleaseExecutionService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Jenkins 回调入口；以 HMAC 签名代替登录态，回调数据只允许更新对应构建记录。 */
@RestController
@RequestMapping("/api/v1/integrations/jenkins")
public class JenkinsCallbackController {
    private final JenkinsCallbackVerifier verifier;
    private final ReleaseExecutionService releaseExecutionService;
    private final String callbackSecret;

    public JenkinsCallbackController(JenkinsCallbackVerifier verifier,
                                     ReleaseExecutionService releaseExecutionService,
                                     @Value("${devops.integration.jenkins.callback-secret:}") String callbackSecret) {
        this.verifier = verifier;
        this.releaseExecutionService = releaseExecutionService;
        this.callbackSecret = callbackSecret;
    }

    @PostMapping("/callback")
    public ApiResponse<Void> callback(
            @RequestHeader(value = "X-DevOps-Signature", required = false) String signature,
            @RequestBody @NotBlank String payload
    ) {
        JenkinsCallbackEvent event = verifier.verifyAndParse(signature, payload, callbackSecret);
        releaseExecutionService.updatePipelineRunStatus(event.projectId(), event.serviceId(), event.pipelineRunId(),
                new UpdatePipelineRunStatusRequest(event.status(), event.jobName(), event.buildNumber(),
                        event.buildUrl(), event.failureSummary()));
        if (event.containsImageArtifact()) {
            ImageArtifactResponse artifact = releaseExecutionService.createImageArtifact(event.projectId(), event.serviceId(),
                    event.pipelineRunId(), new CreateImageArtifactRequest(event.imageRepository(), event.imageTag(), event.imageDigest()));
            releaseExecutionService.updateImageArtifactStatus(event.projectId(), event.serviceId(), Long.valueOf(artifact.id()),
                    new com.vincent.devops.releaseexecution.dto.UpdateImageArtifactStatusRequest("PUBLISHED"));
        }
        return ApiResponse.ok(null);
    }
}
