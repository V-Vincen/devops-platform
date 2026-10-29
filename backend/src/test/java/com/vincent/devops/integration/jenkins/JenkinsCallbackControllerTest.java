package com.vincent.devops.integration.jenkins;

import com.vincent.devops.releaseexecution.dto.ImageArtifactResponse;
import com.vincent.devops.releaseexecution.dto.UpdateImageArtifactStatusRequest;
import com.vincent.devops.releaseexecution.dto.UpdatePipelineRunStatusRequest;
import com.vincent.devops.releaseexecution.service.ReleaseExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JenkinsCallbackControllerTest {

    @Mock
    private JenkinsCallbackVerifier verifier;
    @Mock
    private ReleaseExecutionService releaseExecutionService;

    @Test
    void shouldCreatePublishedImageAfterSuccessfulCallback() {
        JenkinsCallbackEvent event = new JenkinsCallbackEvent(1L, 2L, 3L, "SUCCESS", "job", 4L,
                "http://jenkins/4", null, "nexus.local/demo", "abc123",
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        when(verifier.verifyAndParse("signature", "payload", "secret")).thenReturn(event);
        when(releaseExecutionService.createImageArtifact(eq(1L), eq(2L), eq(3L), any()))
                .thenReturn(new ImageArtifactResponse("5", "1", "2", "3", "nexus.local/demo", "abc123",
                        event.imageDigest(), "PENDING", null, null, null));

        new JenkinsCallbackController(verifier, releaseExecutionService, "secret").callback("signature", "payload");

        InOrder order = inOrder(releaseExecutionService);
        order.verify(releaseExecutionService).updatePipelineRunStatus(1L, 2L, 3L,
                new UpdatePipelineRunStatusRequest("SUCCESS", "job", 4L, "http://jenkins/4", null));
        order.verify(releaseExecutionService).createImageArtifact(eq(1L), eq(2L), eq(3L), any());
        order.verify(releaseExecutionService).updateImageArtifactStatus(1L, 2L, 5L,
                new UpdateImageArtifactStatusRequest("PUBLISHED"));
    }
}
