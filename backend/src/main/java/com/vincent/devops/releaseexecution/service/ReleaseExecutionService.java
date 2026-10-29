package com.vincent.devops.releaseexecution.service;

import com.vincent.devops.releaseexecution.dto.CreateDeploymentRequest;
import com.vincent.devops.releaseexecution.dto.CreateImageArtifactRequest;
import com.vincent.devops.releaseexecution.dto.CreatePipelineRunRequest;
import com.vincent.devops.releaseexecution.dto.DeploymentResponse;
import com.vincent.devops.releaseexecution.dto.ImageArtifactResponse;
import com.vincent.devops.releaseexecution.dto.PipelineRunResponse;
import com.vincent.devops.releaseexecution.dto.UpdateDeploymentStatusRequest;
import com.vincent.devops.releaseexecution.dto.UpdateImageArtifactStatusRequest;
import com.vincent.devops.releaseexecution.dto.UpdatePipelineRunStatusRequest;

import java.util.List;

/** 平台内部的构建、镜像和部署记录服务，不直接调用外部执行器。 */
public interface ReleaseExecutionService {

    PipelineRunResponse createPipelineRun(Long projectId, Long serviceId, CreatePipelineRunRequest request);

    PipelineRunResponse getPipelineRun(Long projectId, Long serviceId, Long pipelineRunId);

    List<PipelineRunResponse> listPipelineRuns(Long projectId, Long serviceId, String status);

    PipelineRunResponse updatePipelineRunStatus(Long projectId, Long serviceId, Long pipelineRunId,
                                                UpdatePipelineRunStatusRequest request);

    ImageArtifactResponse createImageArtifact(Long projectId, Long serviceId, Long pipelineRunId,
                                              CreateImageArtifactRequest request);

    List<ImageArtifactResponse> listImageArtifacts(Long projectId, Long serviceId);

    ImageArtifactResponse updateImageArtifactStatus(Long projectId, Long serviceId, Long imageArtifactId,
                                                    UpdateImageArtifactStatusRequest request);

    DeploymentResponse createDeployment(Long projectId, Long serviceId, Long pipelineRunId,
                                        CreateDeploymentRequest request);

    List<DeploymentResponse> listDeployments(Long projectId, Long serviceId);

    DeploymentResponse updateDeploymentStatus(Long projectId, Long serviceId, Long deploymentId,
                                              UpdateDeploymentStatusRequest request);
}
