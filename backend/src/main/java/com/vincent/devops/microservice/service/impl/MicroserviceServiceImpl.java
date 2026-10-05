package com.vincent.devops.microservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.vincent.devops.common.ResourceNotFoundException;
import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.mapper.EnvironmentMapper;
import com.vincent.devops.environment.service.EnvironmentService;
import com.vincent.devops.microservice.domain.MicroserviceEntity;
import com.vincent.devops.microservice.domain.ServiceEnvironmentEntity;
import com.vincent.devops.microservice.dto.CreateMicroserviceRequest;
import com.vincent.devops.microservice.dto.EnvironmentOptionResponse;
import com.vincent.devops.microservice.dto.MicroserviceEnvironmentRow;
import com.vincent.devops.microservice.dto.UpdateMicroserviceRequest;
import com.vincent.devops.microservice.dto.UpdateMicroserviceStatusRequest;
import com.vincent.devops.microservice.mapper.MicroserviceMapper;
import com.vincent.devops.microservice.mapper.ServiceEnvironmentMapper;
import com.vincent.devops.microservice.service.MicroserviceService;
import com.vincent.devops.project.domain.ProjectEntity;
import com.vincent.devops.project.mapper.ProjectMapper;
import com.vincent.devops.project.service.ProjectService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 微服务管理业务实现。
 */
@Service
public class MicroserviceServiceImpl implements MicroserviceService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";

    private final MicroserviceMapper microserviceMapper;
    private final ServiceEnvironmentMapper serviceEnvironmentMapper;
    private final ProjectMapper projectMapper;
    private final EnvironmentMapper environmentMapper;
    private final ProjectService projectService;
    private final EnvironmentService environmentService;

    public MicroserviceServiceImpl(
            MicroserviceMapper microserviceMapper,
            ServiceEnvironmentMapper serviceEnvironmentMapper,
            ProjectMapper projectMapper,
            EnvironmentMapper environmentMapper,
            ProjectService projectService,
            EnvironmentService environmentService
    ) {
        this.microserviceMapper = microserviceMapper;
        this.serviceEnvironmentMapper = serviceEnvironmentMapper;
        this.projectMapper = projectMapper;
        this.environmentMapper = environmentMapper;
        this.projectService = projectService;
        this.environmentService = environmentService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MicroserviceEntity> page(Long projectId, String keyword, String status, long current, long size) {
        requireProject(projectId);

        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<MicroserviceEntity> wrapper = baseProjectQuery(projectId);
        wrapper.and(StringUtils.hasText(keyword), query -> query
                        .like(MicroserviceEntity::getServiceCode, keyword)
                        .or()
                        .like(MicroserviceEntity::getServiceName, keyword)
                        .or()
                        .like(MicroserviceEntity::getRepositoryUrl, keyword))
                .eq(StringUtils.hasText(status), MicroserviceEntity::getStatus, status)
                .orderByDesc(MicroserviceEntity::getCreatedAt);

        return microserviceMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    /**
     * 全局列表以服务为主表分页，既展示多环境关联，也保留未关联环境的历史服务。
     */
    @Override
    @Transactional(readOnly = true)
    public Page<MicroserviceEnvironmentRow> pageEnvironmentBindings(
            List<Long> accessibleProjectIds,
            Long projectId,
            Long environmentId,
            String keyword,
            String status,
            long current,
            long size
    ) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        return microserviceMapper.selectEnvironmentPage(
                new Page<>(safeCurrent, safeSize),
                accessibleProjectIds,
                projectId,
                environmentId,
                keyword,
                status
        );
    }

    /**
     * 下拉选项只暴露启用项目内的启用环境，避免新建时先提交再被服务端拒绝。
     */
    @Override
    @Transactional(readOnly = true)
    public List<EnvironmentOptionResponse> listAvailableEnvironments(List<Long> accessibleProjectIds) {
        LambdaQueryWrapper<ProjectEntity> projectQuery = new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getStatus, ACTIVE)
                .orderByDesc(ProjectEntity::getCreatedAt);
        if (accessibleProjectIds != null) {
            if (accessibleProjectIds.isEmpty()) {
                return List.of();
            }
            projectQuery.in(ProjectEntity::getId, accessibleProjectIds);
        }
        List<ProjectEntity> projects = projectMapper.selectList(projectQuery);
        if (projects.isEmpty()) {
            return List.of();
        }

        List<Long> projectIds = projects.stream().map(ProjectEntity::getId).toList();
        Map<Long, ProjectEntity> projectById = projects.stream()
                .collect(Collectors.toMap(ProjectEntity::getId, Function.identity()));
        return environmentMapper.selectList(new LambdaQueryWrapper<EnvironmentEntity>()
                        .in(EnvironmentEntity::getProjectId, projectIds)
                        .eq(EnvironmentEntity::getStatus, ACTIVE)
                        .orderByDesc(EnvironmentEntity::getCreatedAt))
                .stream()
                .map(environment -> EnvironmentOptionResponse.from(projectById.get(environment.getProjectId()), environment))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MicroserviceEntity get(Long projectId, Long id) {
        requireProject(projectId);
        return requireMicroservice(projectId, id);
    }

    @Override
    @Transactional
    public MicroserviceEntity create(Long projectId, CreateMicroserviceRequest request) {
        ProjectEntity project = requireProject(projectId);
        requireActiveProject(project);
        ensureServiceCodeNotExists(projectId, request.serviceCode());

        LocalDateTime now = LocalDateTime.now();
        MicroserviceEntity entity = new MicroserviceEntity();
        entity.setProjectId(projectId);
        entity.setServiceCode(request.serviceCode());
        entity.setServiceName(request.serviceName());
        entity.setRepositoryUrl(request.repositoryUrl());
        entity.setBranchName(request.branchName());
        entity.setBuildType(request.buildType());
        entity.setImageRepository(request.imageRepository());
        entity.setPort(request.port());
        entity.setReplicas(request.replicas());
        entity.setCpuLimit(request.cpuLimit());
        entity.setMemoryLimit(request.memoryLimit());
        entity.setStatus(ACTIVE);
        entity.setDescription(request.description());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(false);

        // 数据库唯一索引是最终防线；前置查询用于返回更明确的业务提示。
        microserviceMapper.insert(entity);
        // 创建和多环境关联处于同一事务，任何环境校验或插入失败都会回滚新建服务。
        bindEnvironments(projectId, entity.getId(), request.uniqueEnvironmentIds());
        return entity;
    }

    @Override
    @Transactional
    public MicroserviceEntity update(Long projectId, Long id, UpdateMicroserviceRequest request) {
        requireProject(projectId);
        MicroserviceEntity entity = requireMicroservice(projectId, id);
        entity.setServiceName(request.serviceName());
        entity.setRepositoryUrl(request.repositoryUrl());
        entity.setBranchName(request.branchName());
        entity.setBuildType(request.buildType());
        entity.setImageRepository(request.imageRepository());
        entity.setPort(request.port());
        entity.setReplicas(request.replicas());
        entity.setCpuLimit(request.cpuLimit());
        entity.setMemoryLimit(request.memoryLimit());
        entity.setDescription(request.description());
        entity.setUpdatedAt(LocalDateTime.now());
        microserviceMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public MicroserviceEntity updateStatus(Long projectId, Long id, UpdateMicroserviceStatusRequest request) {
        requireProject(projectId);
        MicroserviceEntity entity = requireMicroservice(projectId, id);
        String targetStatus = request.status();
        if (!ACTIVE.equals(targetStatus) && !DISABLED.equals(targetStatus)) {
            throw new IllegalArgumentException("微服务状态只能是 ACTIVE 或 DISABLED");
        }

        // 重复提交相同状态保持幂等，避免产生无意义的数据库更新。
        if (targetStatus.equals(entity.getStatus())) {
            return entity;
        }

        entity.setStatus(targetStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        microserviceMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long id) {
        requireProject(projectId);
        MicroserviceEntity entity = requireMicroservice(projectId, id);
        if (ACTIVE.equals(entity.getStatus())) {
            throw new IllegalArgumentException("启用中的微服务不能删除，请先停用微服务");
        }

        LocalDateTime now = LocalDateTime.now();
        entity.setUpdatedAt(now);
        microserviceMapper.updateById(entity);
        logicallyDeleteBindings(List.of(id), now);
        microserviceMapper.deleteById(entity.getId());
    }

    /**
     * 所有微服务先完成状态校验，再在同一事务中删除服务与全部环境关联。
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        List<MicroserviceEntity> services = microserviceMapper.selectByIds(ids);
        if (services.size() != ids.size()) {
            throw new ResourceNotFoundException("部分微服务不存在或已删除");
        }
        if (services.stream().anyMatch(service -> ACTIVE.equals(service.getStatus()))) {
            throw new IllegalArgumentException("选中微服务包含启用状态数据，请先全部停用");
        }

        LocalDateTime now = LocalDateTime.now();
        microserviceMapper.update(null, new LambdaUpdateWrapper<MicroserviceEntity>()
                .in(MicroserviceEntity::getId, ids)
                .set(MicroserviceEntity::getUpdatedAt, now));
        logicallyDeleteBindings(ids, now);
        microserviceMapper.deleteByIds(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MicroserviceEntity> listByEnvironment(Long projectId, Long environmentId) {
        requireProject(projectId);
        requireEnvironment(projectId, environmentId);

        List<ServiceEnvironmentEntity> bindings = serviceEnvironmentMapper.selectList(
                baseBindingQuery(projectId).eq(ServiceEnvironmentEntity::getEnvironmentId, environmentId)
                        .orderByDesc(ServiceEnvironmentEntity::getCreatedAt)
        );
        if (bindings.isEmpty()) {
            return List.of();
        }

        List<Long> serviceIds = bindings.stream().map(ServiceEnvironmentEntity::getServiceId).toList();
        Map<Long, MicroserviceEntity> services = microserviceMapper.selectList(
                        baseProjectQuery(projectId).in(MicroserviceEntity::getId, serviceIds)
                ).stream()
                .collect(Collectors.toMap(MicroserviceEntity::getId, Function.identity()));
        // 批量查询后按照关联记录顺序返回，避免前端列表顺序随数据库执行计划变化。
        return serviceIds.stream().map(services::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    @Transactional
    public void bindEnvironment(Long projectId, Long serviceId, Long environmentId) {
        bindEnvironments(projectId, serviceId, List.of(environmentId));
    }

    /**
     * 先一次性校验项目、服务和全部环境，再使用 PostgreSQL 冲突忽略批量写入。
     * 这样重复调用和并发重试都不会产生重复关系，任一校验失败也不会写入半批数据。
     */
    @Override
    @Transactional
    public void bindEnvironments(Long projectId, Long serviceId, List<Long> environmentIds) {
        if (environmentIds == null || environmentIds.isEmpty()) {
            return;
        }
        List<Long> uniqueEnvironmentIds = requireUniqueEnvironmentIds(environmentIds);
        ProjectEntity project = requireProject(projectId);
        requireActiveProject(project);
        MicroserviceEntity service = requireMicroservice(projectId, serviceId);
        requireActiveService(service);
        List<EnvironmentEntity> environments = environmentMapper.selectList(new LambdaQueryWrapper<EnvironmentEntity>()
                .eq(EnvironmentEntity::getProjectId, projectId)
                .in(EnvironmentEntity::getId, uniqueEnvironmentIds));
        if (environments.size() != uniqueEnvironmentIds.size()) {
            throw new ResourceNotFoundException("部分关联环境不存在或不属于当前项目");
        }
        if (environments.stream().anyMatch(environment -> !ACTIVE.equals(environment.getStatus()))) {
            throw new IllegalArgumentException("已停用环境不能关联微服务");
        }

        LocalDateTime now = LocalDateTime.now();
        List<ServiceEnvironmentEntity> bindings = uniqueEnvironmentIds.stream().map(environmentId -> {
            ServiceEnvironmentEntity binding = new ServiceEnvironmentEntity();
            binding.setId(IdWorker.getId());
            binding.setProjectId(projectId);
            binding.setServiceId(serviceId);
            binding.setEnvironmentId(environmentId);
            binding.setCreatedAt(now);
            binding.setUpdatedAt(now);
            binding.setDeleted(false);
            return binding;
        }).toList();
        serviceEnvironmentMapper.insertIgnoreExisting(bindings);
    }

    @Override
    @Transactional
    public void unbindEnvironment(Long projectId, Long serviceId, Long environmentId) {
        requireProject(projectId);
        requireMicroservice(projectId, serviceId);
        requireEnvironment(projectId, environmentId);

        ServiceEnvironmentEntity binding = serviceEnvironmentMapper.selectOne(baseBindingQuery(projectId)
                .eq(ServiceEnvironmentEntity::getServiceId, serviceId)
                .eq(ServiceEnvironmentEntity::getEnvironmentId, environmentId));
        if (binding == null) {
            throw new ResourceNotFoundException("微服务环境关联不存在");
        }
        serviceEnvironmentMapper.deleteById(binding.getId());
    }

    private ProjectEntity requireProject(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("项目 ID 必须是正整数");
        }
        return projectService.get(projectId);
    }

    private EnvironmentEntity requireEnvironment(Long projectId, Long environmentId) {
        if (environmentId == null || environmentId <= 0) {
            throw new IllegalArgumentException("环境 ID 必须是正整数");
        }
        return environmentService.get(projectId, environmentId);
    }

    private MicroserviceEntity requireMicroservice(Long projectId, Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("微服务 ID 必须是正整数");
        }
        MicroserviceEntity entity = microserviceMapper.selectOne(baseProjectQuery(projectId)
                .eq(MicroserviceEntity::getId, id));
        if (entity == null) {
            throw new ResourceNotFoundException("微服务不存在");
        }
        return entity;
    }

    private void ensureServiceCodeNotExists(Long projectId, String serviceCode) {
        boolean exists = microserviceMapper.selectCount(baseProjectQuery(projectId)
                .eq(MicroserviceEntity::getServiceCode, serviceCode)) > 0;
        if (exists) {
            throw new DuplicateKeyException("当前项目下微服务编码已存在");
        }
    }

    private void requireActiveProject(ProjectEntity project) {
        if (!ACTIVE.equals(project.getStatus())) {
            throw new IllegalArgumentException("已停用项目不能新增或关联微服务");
        }
    }

    private void requireActiveService(MicroserviceEntity service) {
        if (!ACTIVE.equals(service.getStatus())) {
            throw new IllegalArgumentException("已停用微服务不能关联运行环境");
        }
    }

    /**
     * 关联集合需要在入库前去重校验，避免接口调用方把同一环境误传多次。
     */
    private List<Long> requireUniqueEnvironmentIds(List<Long> environmentIds) {
        List<Long> uniqueIds = environmentIds.stream().distinct().toList();
        if (uniqueIds.size() != environmentIds.size()) {
            throw new IllegalArgumentException("关联环境不能包含重复 ID");
        }
        return uniqueIds;
    }

    /**
     * 删除微服务时同步逻辑删除关联记录；更新时间先写入，便于后续审计删除发生时间。
     */
    private void logicallyDeleteBindings(List<Long> serviceIds, LocalDateTime now) {
        LambdaQueryWrapper<ServiceEnvironmentEntity> bindingQuery = new LambdaQueryWrapper<ServiceEnvironmentEntity>()
                .in(ServiceEnvironmentEntity::getServiceId, serviceIds);
        serviceEnvironmentMapper.update(null, new LambdaUpdateWrapper<ServiceEnvironmentEntity>()
                .in(ServiceEnvironmentEntity::getServiceId, serviceIds)
                .set(ServiceEnvironmentEntity::getUpdatedAt, now));
        serviceEnvironmentMapper.delete(bindingQuery);
    }

    private LambdaQueryWrapper<MicroserviceEntity> baseProjectQuery(Long projectId) {
        return new LambdaQueryWrapper<MicroserviceEntity>()
                .eq(MicroserviceEntity::getProjectId, projectId);
    }

    private LambdaQueryWrapper<ServiceEnvironmentEntity> baseBindingQuery(Long projectId) {
        return new LambdaQueryWrapper<ServiceEnvironmentEntity>()
                .eq(ServiceEnvironmentEntity::getProjectId, projectId);
    }
}
