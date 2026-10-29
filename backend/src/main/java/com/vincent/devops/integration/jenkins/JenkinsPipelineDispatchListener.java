package com.vincent.devops.integration.jenkins;

import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.service.EnvironmentService;
import com.vincent.devops.releaseconfig.dto.ReleaseConfigurationResponse;
import com.vincent.devops.releaseconfig.service.ReleaseConfigurationService;
import com.vincent.devops.releaseexecution.dto.PipelineRunResponse;
import com.vincent.devops.releaseexecution.dto.UpdatePipelineRunStatusRequest;
import com.vincent.devops.releaseexecution.event.PipelineRunCreatedEvent;
import com.vincent.devops.releaseexecution.service.ReleaseExecutionService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在构建记录提交后调度 Jenkins。
 *
 * <p>调度失败被记录为构建失败而不是抛回创建接口，调用方始终可以从平台查询到失败原因；
 * 调度未启用时保持 PENDING，支持人工或后续执行器接管。</p>
 */
@Component
public class JenkinsPipelineDispatchListener {
    private final JenkinsBuildDispatcher dispatcher;
    private final ReleaseExecutionService releaseExecutionService;
    private final ReleaseConfigurationService releaseConfigurationService;
    private final EnvironmentService environmentService;

    public JenkinsPipelineDispatchListener(JenkinsBuildDispatcher dispatcher,
                                           ReleaseExecutionService releaseExecutionService,
                                           ReleaseConfigurationService releaseConfigurationService,
                                           EnvironmentService environmentService) {
        this.dispatcher = dispatcher;
        this.releaseExecutionService = releaseExecutionService;
        this.releaseConfigurationService = releaseConfigurationService;
        this.environmentService = environmentService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPipelineRunCreated(PipelineRunCreatedEvent event) {
        if (!dispatcher.isEnabled()) {
            return;
        }
        PipelineRunResponse run = releaseExecutionService.getPipelineRun(event.projectId(), event.serviceId(), event.pipelineRunId());
        if (!"PENDING".equals(run.status())) {
            return;
        }
        JenkinsBuildTriggerResult result;
        try {
            ReleaseConfigurationResponse configuration = releaseConfigurationService.get(event.projectId(), event.serviceId());
            EnvironmentEntity environment = environmentService.get(event.projectId(), event.environmentId());
            result = dispatcher.dispatch(buildParameters(event, run, configuration, environment));
        } catch (RuntimeException exception) {
            releaseExecutionService.updatePipelineRunStatus(event.projectId(), event.serviceId(), event.pipelineRunId(),
                    new UpdatePipelineRunStatusRequest("FAILED", safeJobName(), null, null,
                            "Jenkins 构建触发失败，请检查 Jenkins 连接、Job 配置和回调地址"));
            return;
        }
        releaseExecutionService.updatePipelineRunStatus(event.projectId(), event.serviceId(), event.pipelineRunId(),
                new UpdatePipelineRunStatusRequest("PENDING", dispatcher.jobName(), null, result.queueUrl(), null));
    }

    private static Map<String, String> buildParameters(PipelineRunCreatedEvent event, PipelineRunResponse run,
                                                        ReleaseConfigurationResponse configuration, EnvironmentEntity environment) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("PROJECT_ID", event.projectId().toString());
        parameters.put("SERVICE_ID", event.serviceId().toString());
        parameters.put("PIPELINE_RUN_ID", event.pipelineRunId().toString());
        parameters.put("REPOSITORY_URL", configuration.cloneUrl());
        parameters.put("GIT_REF", run.gitRef());
        if (run.commitSha() != null && !run.commitSha().isBlank()) {
            parameters.put("COMMIT_SHA", run.commitSha());
        }
        parameters.put("BUILD_TYPE", configuration.buildType());
        parameters.put("BUILD_WORKDIR", configuration.buildWorkdir());
        parameters.put("DOCKERFILE_PATH", configuration.dockerfilePath());
        parameters.put("DOCKER_CONTEXT_PATH", configuration.dockerContextPath());
        parameters.put("IMAGE_REPOSITORY", configuration.imageRepository());
        parameters.put("ENVIRONMENT_CODE", environment.getCode());
        if (environment.getNamespace() != null && !environment.getNamespace().isBlank()) {
            parameters.put("K8S_NAMESPACE", environment.getNamespace());
        }
        return parameters;
    }

    private String safeJobName() {
        try {
            return dispatcher.jobName();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
