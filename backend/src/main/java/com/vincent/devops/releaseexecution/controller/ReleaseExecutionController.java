package com.vincent.devops.releaseexecution.controller;

import com.vincent.devops.common.ApiResponse;
import com.vincent.devops.releaseexecution.dto.CreateDeploymentRequest;
import com.vincent.devops.releaseexecution.dto.CreateImageArtifactRequest;
import com.vincent.devops.releaseexecution.dto.CreatePipelineRunRequest;
import com.vincent.devops.releaseexecution.dto.DeploymentResponse;
import com.vincent.devops.releaseexecution.dto.ImageArtifactResponse;
import com.vincent.devops.releaseexecution.dto.PipelineRunResponse;
import com.vincent.devops.releaseexecution.dto.UpdateDeploymentStatusRequest;
import com.vincent.devops.releaseexecution.dto.UpdateImageArtifactStatusRequest;
import com.vincent.devops.releaseexecution.dto.UpdatePipelineRunStatusRequest;
import com.vincent.devops.releaseexecution.service.ReleaseExecutionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台内部执行记录接口；不直接暴露 Jenkins、镜像仓库或 K3s 客户端。 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/services/{serviceId}/release-executions")
public class ReleaseExecutionController {
    private final ReleaseExecutionService service;

    public ReleaseExecutionController(ReleaseExecutionService service) {
        this.service = service;
    }

    @PostMapping("/pipeline-runs")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<PipelineRunResponse> createPipelineRun(@PathVariable Long projectId, @PathVariable Long serviceId,
                                                               @Valid @RequestBody CreatePipelineRunRequest request) {
        return ApiResponse.ok(service.createPipelineRun(projectId, serviceId, request));
    }

    @GetMapping("/pipeline-runs")
    @PreAuthorize("@projectAccessService.canRead(authentication, #projectId)")
    public ApiResponse<List<PipelineRunResponse>> listPipelineRuns(@PathVariable Long projectId, @PathVariable Long serviceId,
                                                                   @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listPipelineRuns(projectId, serviceId, status));
    }

    @GetMapping("/pipeline-runs/{pipelineRunId}")
    @PreAuthorize("@projectAccessService.canRead(authentication, #projectId)")
    public ApiResponse<PipelineRunResponse> getPipelineRun(@PathVariable Long projectId, @PathVariable Long serviceId,
                                                           @PathVariable Long pipelineRunId) {
        return ApiResponse.ok(service.getPipelineRun(projectId, serviceId, pipelineRunId));
    }

    @PatchMapping("/pipeline-runs/{pipelineRunId}/status")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<PipelineRunResponse> updatePipelineRunStatus(
            @PathVariable Long projectId, @PathVariable Long serviceId, @PathVariable Long pipelineRunId,
            @Valid @RequestBody UpdatePipelineRunStatusRequest request
    ) {
        return ApiResponse.ok(service.updatePipelineRunStatus(projectId, serviceId, pipelineRunId, request));
    }

    @PostMapping("/pipeline-runs/{pipelineRunId}/image-artifacts")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<ImageArtifactResponse> createImageArtifact(
            @PathVariable Long projectId, @PathVariable Long serviceId, @PathVariable Long pipelineRunId,
            @Valid @RequestBody CreateImageArtifactRequest request
    ) {
        return ApiResponse.ok(service.createImageArtifact(projectId, serviceId, pipelineRunId, request));
    }

    @GetMapping("/image-artifacts")
    @PreAuthorize("@projectAccessService.canRead(authentication, #projectId)")
    public ApiResponse<List<ImageArtifactResponse>> listImageArtifacts(@PathVariable Long projectId, @PathVariable Long serviceId) {
        return ApiResponse.ok(service.listImageArtifacts(projectId, serviceId));
    }

    @PatchMapping("/image-artifacts/{imageArtifactId}/status")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<ImageArtifactResponse> updateImageArtifactStatus(
            @PathVariable Long projectId, @PathVariable Long serviceId, @PathVariable Long imageArtifactId,
            @Valid @RequestBody UpdateImageArtifactStatusRequest request
    ) {
        return ApiResponse.ok(service.updateImageArtifactStatus(projectId, serviceId, imageArtifactId, request));
    }

    @PostMapping("/pipeline-runs/{pipelineRunId}/deployments")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<DeploymentResponse> createDeployment(
            @PathVariable Long projectId, @PathVariable Long serviceId, @PathVariable Long pipelineRunId,
            @Valid @RequestBody CreateDeploymentRequest request
    ) {
        return ApiResponse.ok(service.createDeployment(projectId, serviceId, pipelineRunId, request));
    }

    @GetMapping("/deployments")
    @PreAuthorize("@projectAccessService.canRead(authentication, #projectId)")
    public ApiResponse<List<DeploymentResponse>> listDeployments(@PathVariable Long projectId, @PathVariable Long serviceId) {
        return ApiResponse.ok(service.listDeployments(projectId, serviceId));
    }

    @PatchMapping("/deployments/{deploymentId}/status")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<DeploymentResponse> updateDeploymentStatus(
            @PathVariable Long projectId, @PathVariable Long serviceId, @PathVariable Long deploymentId,
            @Valid @RequestBody UpdateDeploymentStatusRequest request
    ) {
        return ApiResponse.ok(service.updateDeploymentStatus(projectId, serviceId, deploymentId, request));
    }
}
