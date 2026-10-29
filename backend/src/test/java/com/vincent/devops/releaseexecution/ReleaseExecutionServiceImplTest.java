package com.vincent.devops.releaseexecution;

import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.service.EnvironmentService;
import com.vincent.devops.microservice.domain.MicroserviceEntity;
import com.vincent.devops.microservice.service.MicroserviceService;
import com.vincent.devops.project.domain.ProjectEntity;
import com.vincent.devops.project.service.ProjectService;
import com.vincent.devops.releaseconfig.dto.ReleaseConfigurationRow;
import com.vincent.devops.releaseconfig.mapper.ReleaseConfigurationMapper;
import com.vincent.devops.releaseexecution.dto.CreatePipelineRunRequest;
import com.vincent.devops.releaseexecution.dto.PipelineRunRow;
import com.vincent.devops.releaseexecution.dto.UpdatePipelineRunStatusRequest;
import com.vincent.devops.releaseexecution.event.PipelineRunCreatedEvent;
import com.vincent.devops.releaseexecution.mapper.ReleaseExecutionMapper;
import com.vincent.devops.releaseexecution.service.impl.ReleaseExecutionServiceImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseExecutionServiceImplTest {

    @Mock
    private ReleaseExecutionMapper mapper;

    @Mock
    private ReleaseConfigurationMapper releaseConfigurationMapper;

    @Mock
    private ProjectService projectService;

    @Mock
    private MicroserviceService microserviceService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReleaseExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReleaseExecutionServiceImpl(mapper, releaseConfigurationMapper, projectService,
                microserviceService, environmentService, eventPublisher);
        when(projectService.get(1L)).thenReturn(project());
        when(microserviceService.get(1L, 2L)).thenReturn(microservice());
    }

    @Test
    void shouldCreatePendingPipelineRunWithNormalizedSnapshot() {
        when(environmentService.get(1L, 3L)).thenReturn(environment());
        when(releaseConfigurationMapper.selectConfiguration(1L, 2L)).thenReturn(configuration());
        when(mapper.existsActivePolicy(1L, 2L, 3L)).thenReturn(true);
        PipelineRunRow row = pipelineRun("PENDING");
        when(mapper.selectPipelineRun(any(), eq(2L), any())).thenReturn(row);

        var response = service.createPipelineRun(1L, 2L, new CreatePipelineRunRequest(
                3L, "manual", null, "main", "abc123", " {\"module\":\"api\"} "
        ));

        assertEquals("PENDING", response.status());
        verify(mapper).insertPipelineRun(any(), eq(1L), eq(2L), eq(3L), eq(10L), eq(11L),
                eq("MANUAL"), eq(null), eq("main"), eq("abc123"), eq("{\"module\":\"api\"}"), any());
        verify(eventPublisher).publishEvent(any(PipelineRunCreatedEvent.class));
    }

    @Test
    void shouldReturnExistingWebhookRunWhenRequestIsRetried() {
        when(environmentService.get(1L, 3L)).thenReturn(environment());
        when(releaseConfigurationMapper.selectConfiguration(1L, 2L)).thenReturn(configuration());
        when(mapper.existsActivePolicy(1L, 2L, 3L)).thenReturn(true);
        PipelineRunRow row = pipelineRun("PENDING", "event-1");
        when(mapper.selectPipelineRunByExternalEvent(10L, "event-1")).thenReturn(row);

        var response = service.createPipelineRun(1L, 2L, new CreatePipelineRunRequest(
                3L, "WEBHOOK", "event-1", "refs/heads/main", null, null
        ));

        assertEquals("event-1", response.externalEventId());
        verify(mapper, never()).insertPipelineRun(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectIllegalPipelineTransitionAndMissingFailureSummary() {
        when(mapper.selectPipelineRun(1L, 2L, 20L)).thenReturn(pipelineRun("PENDING"));

        assertThrows(IllegalArgumentException.class, () -> service.updatePipelineRunStatus(1L, 2L, 20L,
                new UpdatePipelineRunStatusRequest("SUCCESS", null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.updatePipelineRunStatus(1L, 2L, 20L,
                new UpdatePipelineRunStatusRequest("FAILED", null, null, null, null)));
        verify(mapper, never()).updatePipelineRunStatus(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private ProjectEntity project() {
        ProjectEntity entity = new ProjectEntity();
        entity.setId(1L);
        entity.setStatus("ACTIVE");
        entity.setDeleted(false);
        return entity;
    }

    private MicroserviceEntity microservice() {
        MicroserviceEntity entity = new MicroserviceEntity();
        entity.setId(2L);
        entity.setProjectId(1L);
        entity.setStatus("ACTIVE");
        entity.setDeleted(false);
        return entity;
    }

    private EnvironmentEntity environment() {
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.setId(3L);
        entity.setProjectId(1L);
        entity.setStatus("ACTIVE");
        entity.setDeleted(false);
        return entity;
    }

    private ReleaseConfigurationRow configuration() {
        return new ReleaseConfigurationRow("2", "10", "demo-service", "GITHUB",
                "https://github.com/example/demo.git", "main", null, "11", "/", "[\"/**\"]",
                ".", "MAVEN", null, "Dockerfile", ".", "nexus.local/demo");
    }

    private PipelineRunRow pipelineRun(String status) {
        return pipelineRun(status, null);
    }

    private PipelineRunRow pipelineRun(String status, String externalEventId) {
        LocalDateTime now = LocalDateTime.now();
        return new PipelineRunRow("20", "1", "2", "3", "10", "11", "MANUAL", externalEventId,
                "main", null, status, null, null, null, "{}", now, null, null, null, now, now);
    }
}
