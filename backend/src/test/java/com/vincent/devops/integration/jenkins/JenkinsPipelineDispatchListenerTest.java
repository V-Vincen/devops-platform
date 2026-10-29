package com.vincent.devops.integration.jenkins;

import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.service.EnvironmentService;
import com.vincent.devops.releaseconfig.dto.ReleaseConfigurationResponse;
import com.vincent.devops.releaseconfig.service.ReleaseConfigurationService;
import com.vincent.devops.releaseexecution.dto.PipelineRunResponse;
import com.vincent.devops.releaseexecution.dto.UpdatePipelineRunStatusRequest;
import com.vincent.devops.releaseexecution.event.PipelineRunCreatedEvent;
import com.vincent.devops.releaseexecution.service.ReleaseExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JenkinsPipelineDispatchListenerTest {

    @Mock
    private JenkinsBuildDispatcher dispatcher;
    @Mock
    private ReleaseExecutionService releaseExecutionService;
    @Mock
    private ReleaseConfigurationService releaseConfigurationService;
    @Mock
    private EnvironmentService environmentService;

    @Test
    void shouldDispatchCommittedPipelineAndRecordQueueUrl() {
        JenkinsPipelineDispatchListener listener = listener();
        when(dispatcher.isEnabled()).thenReturn(true);
        when(releaseExecutionService.getPipelineRun(1L, 2L, 4L)).thenReturn(pipelineRun());
        when(releaseConfigurationService.get(1L, 2L)).thenReturn(configuration());
        when(environmentService.get(1L, 3L)).thenReturn(environment());
        when(dispatcher.dispatch(any())).thenReturn(new JenkinsBuildTriggerResult(201, "http://jenkins/queue/item/8"));
        when(dispatcher.jobName()).thenReturn("devops-platform-server");

        listener.onPipelineRunCreated(new PipelineRunCreatedEvent(1L, 2L, 3L, 4L));

        ArgumentCaptor<Map<String, String>> parameters = stringMapCaptor();
        verify(dispatcher).dispatch(parameters.capture());
        assertEquals("4", parameters.getValue().get("PIPELINE_RUN_ID"));
        assertEquals("abc123", parameters.getValue().get("COMMIT_SHA"));
        assertEquals("test", parameters.getValue().get("ENVIRONMENT_CODE"));
        verify(releaseExecutionService).updatePipelineRunStatus(eq(1L), eq(2L), eq(4L),
                eq(new UpdatePipelineRunStatusRequest("PENDING", "devops-platform-server", null,
                        "http://jenkins/queue/item/8", null)));
    }

    @Test
    void shouldPersistFailureWhenJenkinsDispatchFails() {
        JenkinsPipelineDispatchListener listener = listener();
        when(dispatcher.isEnabled()).thenReturn(true);
        when(releaseExecutionService.getPipelineRun(1L, 2L, 4L)).thenReturn(pipelineRun());
        when(releaseConfigurationService.get(1L, 2L)).thenReturn(configuration());
        when(environmentService.get(1L, 3L)).thenReturn(environment());
        when(dispatcher.dispatch(any())).thenThrow(new IllegalStateException("connection refused"));
        when(dispatcher.jobName()).thenReturn("devops-platform-server");

        listener.onPipelineRunCreated(new PipelineRunCreatedEvent(1L, 2L, 3L, 4L));

        verify(releaseExecutionService).updatePipelineRunStatus(eq(1L), eq(2L), eq(4L),
                eq(new UpdatePipelineRunStatusRequest("FAILED", "devops-platform-server", null, null,
                        "Jenkins 构建触发失败，请检查 Jenkins 连接、Job 配置和回调地址")));
    }

    @Test
    void shouldOmitCommitShaWhenSourceEventDoesNotProvideOne() {
        JenkinsPipelineDispatchListener listener = listener();
        when(dispatcher.isEnabled()).thenReturn(true);
        when(releaseExecutionService.getPipelineRun(1L, 2L, 4L)).thenReturn(pipelineRun(null));
        when(releaseConfigurationService.get(1L, 2L)).thenReturn(configuration());
        when(environmentService.get(1L, 3L)).thenReturn(environment());
        when(dispatcher.dispatch(any())).thenReturn(new JenkinsBuildTriggerResult(201, "http://jenkins/queue/item/8"));
        when(dispatcher.jobName()).thenReturn("devops-platform-server");

        listener.onPipelineRunCreated(new PipelineRunCreatedEvent(1L, 2L, 3L, 4L));

        ArgumentCaptor<Map<String, String>> parameters = stringMapCaptor();
        verify(dispatcher).dispatch(parameters.capture());
        assertFalse(parameters.getValue().containsKey("COMMIT_SHA"));
    }

    @Test
    void shouldNotDispatchWhenIntegrationIsDisabled() {
        JenkinsPipelineDispatchListener listener = listener();
        when(dispatcher.isEnabled()).thenReturn(false);

        listener.onPipelineRunCreated(new PipelineRunCreatedEvent(1L, 2L, 3L, 4L));

        verify(releaseExecutionService, never()).getPipelineRun(any(), any(), any());
        verify(dispatcher, never()).dispatch(any());
    }

    private JenkinsPipelineDispatchListener listener() {
        return new JenkinsPipelineDispatchListener(dispatcher, releaseExecutionService, releaseConfigurationService, environmentService);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<Map<String, String>> stringMapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    private static PipelineRunResponse pipelineRun() {
        return pipelineRun("abc123");
    }

    private static PipelineRunResponse pipelineRun(String commitSha) {
        return new PipelineRunResponse("4", "1", "2", "3", "10", "11", "MANUAL", null,
                "refs/heads/main", commitSha, "PENDING", null, null, null, "{}",
                "2026-10-28T12:00:00", null, null, null, "2026-10-28T12:00:00", "2026-10-28T12:00:00");
    }

    private static ReleaseConfigurationResponse configuration() {
        return new ReleaseConfigurationResponse("2", "10", "demo", "GITHUB", "https://github.com/example/demo.git",
                "main", null, "11", "/", "[\"/**\"]", ".", "MAVEN", null, "Dockerfile", ".",
                "nexus.local/demo", List.of());
    }

    private static EnvironmentEntity environment() {
        EnvironmentEntity environment = new EnvironmentEntity();
        environment.setCode("test");
        environment.setNamespace("test");
        return environment;
    }
}
