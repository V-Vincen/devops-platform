package com.vincent.devops.integration.github;

import com.vincent.devops.releaseconfig.dto.WebhookReleaseTarget;
import com.vincent.devops.releaseconfig.mapper.ReleaseConfigurationMapper;
import com.vincent.devops.releaseexecution.dto.PipelineRunResponse;
import com.vincent.devops.releaseexecution.service.ReleaseExecutionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubWebhookProcessorTest {

    @Test
    void shouldCreateOneRunWhenBranchAndChangedPathMatch() {
        ReleaseConfigurationMapper mapper = mock(ReleaseConfigurationMapper.class);
        ReleaseExecutionService service = mock(ReleaseExecutionService.class);
        when(mapper.selectWebhookTargets("GITHUB", "acme/demo")).thenReturn(List.of(
                new WebhookReleaseTarget("10", "1", "2", "3", "main", "/", "[\"/**\"]", "AUTO_BUILD_MANUAL_DEPLOY")
        ));
        when(service.createPipelineRun(any(), any(), any())).thenReturn(new PipelineRunResponse(
                "100", "1", "2", "3", "10", "11", "WEBHOOK", "delivery-1", "refs/heads/main", "abc",
                "PENDING", null, null, null, "{\"changedFileCount\":1}", null, null, null, null, null, null
        ));

        GitHubWebhookProcessResult result = new GitHubWebhookProcessor(mapper, service).process(
                new GitHubWebhookEvent("delivery-1", "acme/demo", "refs/heads/main", "abc", List.of("src/App.java"))
        );

        assertEquals("CREATED", result.result());
        assertEquals(List.of("100"), result.pipelineRunIds());
        verify(service).createPipelineRun(any(), any(), any());
    }

    @Test
    void shouldIgnoreManualOnlyOrWrongBranchTarget() {
        ReleaseConfigurationMapper mapper = mock(ReleaseConfigurationMapper.class);
        ReleaseExecutionService service = mock(ReleaseExecutionService.class);
        when(mapper.selectWebhookTargets("GITHUB", "acme/demo")).thenReturn(List.of(
                new WebhookReleaseTarget("10", "1", "2", "3", "main", "/", "[\"/**\"]", "MANUAL_ONLY")
        ));

        GitHubWebhookProcessResult result = new GitHubWebhookProcessor(mapper, service).process(
                new GitHubWebhookEvent("delivery-1", "acme/demo", "refs/heads/feature", "abc", List.of("src/App.java"))
        );

        assertEquals("IGNORED", result.result());
        assertEquals(List.of(), result.pipelineRunIds());
    }
}
