package com.vincent.devops.releaseexecution.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vincent.devops.common.ResourceNotFoundException;
import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.service.EnvironmentService;
import com.vincent.devops.microservice.domain.MicroserviceEntity;
import com.vincent.devops.microservice.service.MicroserviceService;
import com.vincent.devops.project.domain.ProjectEntity;
import com.vincent.devops.project.service.ProjectService;
import com.vincent.devops.releaseconfig.dto.ReleaseConfigurationRow;
import com.vincent.devops.releaseconfig.mapper.ReleaseConfigurationMapper;
import com.vincent.devops.releaseexecution.dto.CreateDeploymentRequest;
import com.vincent.devops.releaseexecution.dto.CreateImageArtifactRequest;
import com.vincent.devops.releaseexecution.dto.CreatePipelineRunRequest;
import com.vincent.devops.releaseexecution.dto.DeploymentResponse;
import com.vincent.devops.releaseexecution.dto.DeploymentRow;
import com.vincent.devops.releaseexecution.dto.ImageArtifactResponse;
import com.vincent.devops.releaseexecution.dto.ImageArtifactRow;
import com.vincent.devops.releaseexecution.dto.PipelineRunResponse;
import com.vincent.devops.releaseexecution.dto.PipelineRunRow;
import com.vincent.devops.releaseexecution.dto.UpdateDeploymentStatusRequest;
import com.vincent.devops.releaseexecution.dto.UpdateImageArtifactStatusRequest;
import com.vincent.devops.releaseexecution.dto.UpdatePipelineRunStatusRequest;
import com.vincent.devops.releaseexecution.event.PipelineRunCreatedEvent;
import com.vincent.devops.releaseexecution.mapper.ReleaseExecutionMapper;
import com.vincent.devops.releaseexecution.service.ReleaseExecutionService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 平台执行记录实现。
 *
 * <p>状态记录先在平台内落库，再由后续 integration 模块对接 Jenkins、镜像仓库和 K3s；
 * 这样外部系统短暂不可用时，平台仍能保留可追踪的业务请求。</p>
 */
@Service
public class ReleaseExecutionServiceImpl implements ReleaseExecutionService {
    private static final String ACTIVE = "ACTIVE";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> PIPELINE_STATUSES = Set.of("PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELED");
    private static final Set<String> IMAGE_STATUSES = Set.of("PENDING", "PUBLISHED", "FAILED");
    private static final Set<String> DEPLOYMENT_STATUSES = Set.of("PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELED");
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of("token", "password", "secret", "privatekey", "credential");

    private final ReleaseExecutionMapper mapper;
    private final ReleaseConfigurationMapper releaseConfigurationMapper;
    private final ProjectService projectService;
    private final MicroserviceService microserviceService;
    private final EnvironmentService environmentService;
    private final ApplicationEventPublisher eventPublisher;

    public ReleaseExecutionServiceImpl(ReleaseExecutionMapper mapper,
                                       ReleaseConfigurationMapper releaseConfigurationMapper,
                                       ProjectService projectService,
                                       MicroserviceService microserviceService,
                                       EnvironmentService environmentService,
                                       ApplicationEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.releaseConfigurationMapper = releaseConfigurationMapper;
        this.projectService = projectService;
        this.microserviceService = microserviceService;
        this.environmentService = environmentService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public PipelineRunResponse createPipelineRun(Long projectId, Long serviceId, CreatePipelineRunRequest request) {
        requireActiveProject(projectId);
        requireActiveService(projectId, serviceId);
        ReleaseConfigurationRow configuration = requireConfiguration(projectId, serviceId);
        EnvironmentEntity environment = environmentService.get(projectId, request.environmentId());
        if (!ACTIVE.equals(environment.getStatus()) || !mapper.existsActivePolicy(projectId, serviceId, environment.getId())) {
            throw new IllegalArgumentException("只能为已关联且启用的环境创建构建记录");
        }

        String triggerType = request.triggerType().trim().toUpperCase(Locale.ROOT);
        String externalEventId = request.normalizedExternalEventId();
        if ("WEBHOOK".equals(triggerType) && externalEventId == null) {
            throw new IllegalArgumentException("Webhook 触发必须提供外部事件 ID");
        }
        String parameterSnapshot = validateParameterSnapshot(request.normalizedParameterSnapshot());
        if (externalEventId != null) {
            PipelineRunRow existing = mapper.selectPipelineRunByExternalEvent(parseId(configuration.repositoryId()), externalEventId);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Long runId = IdWorker.getId();
        try {
            mapper.insertPipelineRun(runId, projectId, serviceId, environment.getId(), parseId(configuration.repositoryId()),
                    parseId(configuration.buildSpecId()), triggerType, externalEventId, request.gitRef().trim(),
                    request.normalizedCommitSha(), parameterSnapshot, now);
        } catch (DuplicateKeyException exception) {
            // 并发重试由唯一外部事件索引收敛为同一条记录，再读取已提交结果。
            PipelineRunRow existing = mapper.selectPipelineRunByExternalEvent(parseId(configuration.repositoryId()), externalEventId);
            if (existing != null) {
                return toResponse(existing);
            }
            throw exception;
        }
        PipelineRunResponse response = getPipelineRun(projectId, serviceId, runId);
        eventPublisher.publishEvent(new PipelineRunCreatedEvent(projectId, serviceId, environment.getId(), runId));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PipelineRunResponse getPipelineRun(Long projectId, Long serviceId, Long pipelineRunId) {
        requireProject(projectId);
        requireService(projectId, serviceId);
        return toResponse(requirePipelineRun(projectId, serviceId, pipelineRunId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PipelineRunResponse> listPipelineRuns(Long projectId, Long serviceId, String status) {
        requireProject(projectId);
        requireService(projectId, serviceId);
        String normalizedStatus = normalizeOptionalStatus(status, PIPELINE_STATUSES, "构建状态");
        return mapper.selectPipelineRuns(projectId, serviceId, normalizedStatus).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public PipelineRunResponse updatePipelineRunStatus(Long projectId, Long serviceId, Long pipelineRunId,
                                                       UpdatePipelineRunStatusRequest request) {
        PipelineRunRow current = requirePipelineRun(projectId, serviceId, pipelineRunId);
        String target = request.status().trim().toUpperCase(Locale.ROOT);
        requireTransition(current.status(), target, "构建", Set.of("PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELED"));
        if ("FAILED".equals(target) && (request.failureSummary() == null || request.failureSummary().isBlank())) {
            throw new IllegalArgumentException("构建失败时必须提供失败摘要");
        }
        if (target.equals(current.status()) && request.jenkinsJobName() == null && request.jenkinsBuildNumber() == null
                && request.jenkinsBuildUrl() == null && request.failureSummary() == null) {
            return toResponse(current);
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = mapper.updatePipelineRunStatus(projectId, serviceId, pipelineRunId, target, current.status(),
                blankToNull(request.jenkinsJobName()), request.jenkinsBuildNumber(), blankToNull(request.jenkinsBuildUrl()),
                "RUNNING".equals(target) ? now : null,
                isTerminal(target) ? now : null, blankToNull(request.failureSummary()), now);
        if (updated != 1) {
            if (target.equals(current.status())) {
                return getPipelineRun(projectId, serviceId, pipelineRunId);
            }
            throw new IllegalArgumentException("构建状态已被其他请求更新，请刷新后重试");
        }
        return getPipelineRun(projectId, serviceId, pipelineRunId);
    }

    @Override
    @Transactional
    public ImageArtifactResponse createImageArtifact(Long projectId, Long serviceId, Long pipelineRunId,
                                                     CreateImageArtifactRequest request) {
        PipelineRunRow run = requirePipelineRun(projectId, serviceId, pipelineRunId);
        if (!"SUCCESS".equals(run.status())) {
            throw new IllegalArgumentException("只有构建成功后才能创建镜像制品记录");
        }
        ReleaseConfigurationRow configuration = requireConfiguration(projectId, serviceId);
        if (!configuration.imageRepository().equals(request.imageRepository().trim())) {
            throw new IllegalArgumentException("镜像仓库必须与服务发布配置一致");
        }
        ImageArtifactRow existing = mapper.selectImageArtifactByDigest(request.imageRepository().trim(), request.imageDigest().trim());
        if (existing != null) {
            if (!existing.serviceId().equals(String.valueOf(serviceId)) || !existing.pipelineRunId().equals(String.valueOf(pipelineRunId))) {
                throw new DuplicateKeyException("该镜像 Digest 已属于其他构建记录");
            }
            return toResponse(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        Long artifactId = IdWorker.getId();
        mapper.insertImageArtifact(artifactId, projectId, serviceId, pipelineRunId, request.imageRepository().trim(),
                request.imageTag().trim(), request.imageDigest().trim(), now);
        return getImageArtifact(projectId, serviceId, artifactId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImageArtifactResponse> listImageArtifacts(Long projectId, Long serviceId) {
        requireProject(projectId);
        requireService(projectId, serviceId);
        return mapper.selectImageArtifacts(projectId, serviceId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ImageArtifactResponse updateImageArtifactStatus(Long projectId, Long serviceId, Long imageArtifactId,
                                                           UpdateImageArtifactStatusRequest request) {
        ImageArtifactRow current = getImageArtifactRow(projectId, serviceId, imageArtifactId);
        String target = request.status().trim().toUpperCase(Locale.ROOT);
        requireTransition(current.status(), target, "镜像", Set.of("PENDING", "PUBLISHED", "FAILED"));
        if (target.equals(current.status())) {
            return toResponse(current);
        }
        int updated = mapper.updateImageArtifactStatus(projectId, serviceId, imageArtifactId, target, current.status(), LocalDateTime.now());
        if (updated != 1) {
            throw new IllegalArgumentException("镜像状态已被其他请求更新，请刷新后重试");
        }
        return getImageArtifact(projectId, serviceId, imageArtifactId);
    }

    @Override
    @Transactional
    public DeploymentResponse createDeployment(Long projectId, Long serviceId, Long pipelineRunId,
                                               CreateDeploymentRequest request) {
        PipelineRunRow run = requirePipelineRun(projectId, serviceId, pipelineRunId);
        if (!"SUCCESS".equals(run.status())) {
            throw new IllegalArgumentException("只有构建成功后才能创建部署记录");
        }
        Long artifactId = parseId(request.imageArtifactId());
        ImageArtifactRow artifact = getImageArtifactRow(projectId, serviceId, artifactId);
        if (!"PUBLISHED".equals(artifact.status())) {
            throw new IllegalArgumentException("只有已发布的镜像制品才能部署");
        }
        String operationType = request.operationType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DEPLOY", "ROLLBACK").contains(operationType)) {
            throw new IllegalArgumentException("部署操作类型只能是 DEPLOY 或 ROLLBACK");
        }
        Long previousDeploymentId = request.previousDeploymentId() == null || request.previousDeploymentId().isBlank()
                ? null : parseId(request.previousDeploymentId());
        if ("ROLLBACK".equals(operationType) && previousDeploymentId == null) {
            throw new IllegalArgumentException("回滚必须提供上一次成功部署记录");
        }
        if (previousDeploymentId != null) {
            DeploymentRow previous = requireDeployment(projectId, serviceId, previousDeploymentId);
            if (!run.environmentId().equals(previous.environmentId()) || !"SUCCESS".equals(previous.status())) {
                throw new IllegalArgumentException("回滚目标必须是同一环境的成功部署记录");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Long deploymentId = IdWorker.getId();
        DeploymentRow existing = mapper.selectDeploymentByRequest(projectId, serviceId, pipelineRunId, artifactId,
                operationType, previousDeploymentId);
        if (existing != null) {
            return toResponse(existing);
        }
        mapper.insertDeployment(deploymentId, projectId, serviceId, parseId(run.environmentId()), pipelineRunId, artifactId,
                previousDeploymentId, operationType, blankToNull(request.helmReleaseName()), now);
        return getDeployment(projectId, serviceId, deploymentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeploymentResponse> listDeployments(Long projectId, Long serviceId) {
        requireProject(projectId);
        requireService(projectId, serviceId);
        return mapper.selectDeployments(projectId, serviceId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public DeploymentResponse updateDeploymentStatus(Long projectId, Long serviceId, Long deploymentId,
                                                     UpdateDeploymentStatusRequest request) {
        DeploymentRow current = requireDeployment(projectId, serviceId, deploymentId);
        String target = request.status().trim().toUpperCase(Locale.ROOT);
        requireTransition(current.status(), target, "部署", Set.of("PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELED"));
        if ("FAILED".equals(target) && (request.failureSummary() == null || request.failureSummary().isBlank())) {
            throw new IllegalArgumentException("部署失败时必须提供失败摘要");
        }
        if (target.equals(current.status())) {
            return toResponse(current);
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = mapper.updateDeploymentStatus(projectId, serviceId, deploymentId, target, current.status(), request.helmRevision(),
                "RUNNING".equals(target) ? now : null, isTerminal(target) ? now : null,
                blankToNull(request.failureSummary()), now);
        if (updated != 1) {
            throw new IllegalArgumentException("部署状态已被其他请求更新，请刷新后重试");
        }
        return getDeployment(projectId, serviceId, deploymentId);
    }

    private ReleaseConfigurationRow requireConfiguration(Long projectId, Long serviceId) {
        ReleaseConfigurationRow configuration = releaseConfigurationMapper.selectConfiguration(projectId, serviceId);
        if (configuration == null) {
            throw new ResourceNotFoundException("未找到微服务发布配置");
        }
        return configuration;
    }

    private PipelineRunRow requirePipelineRun(Long projectId, Long serviceId, Long id) {
        requireProject(projectId);
        requireService(projectId, serviceId);
        PipelineRunRow row = mapper.selectPipelineRun(projectId, serviceId, id);
        if (row == null) {
            throw new ResourceNotFoundException("构建记录不存在");
        }
        return row;
    }

    private ImageArtifactRow getImageArtifactRow(Long projectId, Long serviceId, Long id) {
        requireProject(projectId);
        requireService(projectId, serviceId);
        ImageArtifactRow row = mapper.selectImageArtifact(projectId, serviceId, id);
        if (row == null) {
            throw new ResourceNotFoundException("镜像制品记录不存在");
        }
        return row;
    }

    private DeploymentRow requireDeployment(Long projectId, Long serviceId, Long id) {
        requireProject(projectId);
        requireService(projectId, serviceId);
        DeploymentRow row = mapper.selectDeployment(projectId, serviceId, id);
        if (row == null) {
            throw new ResourceNotFoundException("部署记录不存在");
        }
        return row;
    }

    private ImageArtifactResponse getImageArtifact(Long projectId, Long serviceId, Long id) {
        return toResponse(getImageArtifactRow(projectId, serviceId, id));
    }

    private DeploymentResponse getDeployment(Long projectId, Long serviceId, Long id) {
        return toResponse(requireDeployment(projectId, serviceId, id));
    }

    private ProjectEntity requireProject(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("项目 ID 必须是正整数");
        }
        return projectService.get(projectId);
    }

    private void requireActiveProject(Long projectId) {
        if (!ACTIVE.equals(requireProject(projectId).getStatus())) {
            throw new IllegalArgumentException("已停用项目不能创建执行记录");
        }
    }

    private MicroserviceEntity requireService(Long projectId, Long serviceId) {
        MicroserviceEntity service = microserviceService.get(projectId, serviceId);
        if (service == null) {
            throw new ResourceNotFoundException("微服务不存在");
        }
        return service;
    }

    private void requireActiveService(Long projectId, Long serviceId) {
        if (!ACTIVE.equals(requireService(projectId, serviceId).getStatus())) {
            throw new IllegalArgumentException("已停用微服务不能创建执行记录");
        }
    }

    private static String normalizeOptionalStatus(String status, Set<String> allowed, String label) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(label + "不正确");
        }
        return normalized;
    }

    private static void requireTransition(String current, String target, String label, Set<String> statuses) {
        if (!statuses.contains(target)) {
            throw new IllegalArgumentException(label + "状态不正确");
        }
        if (current.equals(target)) {
            return;
        }
        boolean allowed = switch (current) {
            case "PENDING" -> "RUNNING".equals(target) || "FAILED".equals(target) || "CANCELED".equals(target)
                    || "PUBLISHED".equals(target);
            case "RUNNING" -> "SUCCESS".equals(target) || "FAILED".equals(target) || "CANCELED".equals(target);
            default -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException(label + "状态不能从 " + current + " 变更为 " + target);
        }
    }

    private static boolean isTerminal(String status) {
        return Set.of("SUCCESS", "FAILED", "CANCELED").contains(status);
    }

    private static String validateParameterSnapshot(String value) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(value);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("执行参数快照必须是 JSON 对象");
            }
            if (containsSensitiveField(root)) {
                throw new IllegalArgumentException("执行参数快照不能包含 Token、密码或凭据字段");
            }
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("执行参数快照必须是合法 JSON 对象");
        }
    }

    private static boolean containsSensitiveField(JsonNode node) {
        if (node.isObject()) {
            for (var entry : node.properties()) {
                String name = entry.getKey().toLowerCase(Locale.ROOT).replace("_", "");
                if (SENSITIVE_FIELD_NAMES.stream().anyMatch(name::contains) || containsSensitiveField(entry.getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsSensitiveField(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ID 必须是正整数");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PipelineRunResponse toResponse(PipelineRunRow row) {
        return new PipelineRunResponse(row.id(), row.projectId(), row.serviceId(), row.environmentId(), row.repositoryId(),
                row.buildSpecId(), row.triggerType(), row.externalEventId(), row.gitRef(), row.commitSha(), row.status(),
                row.jenkinsJobName(), row.jenkinsBuildNumber(), row.jenkinsBuildUrl(), row.parameterSnapshot(),
                text(row.queuedAt()), text(row.startedAt()), text(row.finishedAt()), row.failureSummary(),
                text(row.createdAt()), text(row.updatedAt()));
    }

    private ImageArtifactResponse toResponse(ImageArtifactRow row) {
        return new ImageArtifactResponse(row.id(), row.projectId(), row.serviceId(), row.pipelineRunId(), row.imageRepository(),
                row.imageTag(), row.imageDigest(), row.status(), text(row.publishedAt()), text(row.createdAt()), text(row.updatedAt()));
    }

    private DeploymentResponse toResponse(DeploymentRow row) {
        return new DeploymentResponse(row.id(), row.projectId(), row.serviceId(), row.environmentId(), row.pipelineRunId(),
                row.imageArtifactId(), row.previousDeploymentId(), row.operationType(), row.status(), row.helmReleaseName(),
                row.helmRevision(), text(row.deployedAt()), text(row.finishedAt()), row.failureSummary(),
                text(row.createdAt()), text(row.updatedAt()));
    }

    private static String text(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
