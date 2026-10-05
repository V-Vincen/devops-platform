package com.vincent.devops.environment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.common.ResourceNotFoundException;
import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.dto.CreateEnvironmentRequest;
import com.vincent.devops.environment.dto.EnvironmentProjectRow;
import com.vincent.devops.environment.dto.UpdateEnvironmentRequest;
import com.vincent.devops.environment.dto.UpdateEnvironmentStatusRequest;
import com.vincent.devops.environment.mapper.EnvironmentMapper;
import com.vincent.devops.microservice.domain.ServiceEnvironmentEntity;
import com.vincent.devops.microservice.mapper.ServiceEnvironmentMapper;
import com.vincent.devops.environment.service.EnvironmentService;
import com.vincent.devops.project.domain.ProjectEntity;
import com.vincent.devops.project.service.ProjectService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EnvironmentServiceImpl implements EnvironmentService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";

    private final EnvironmentMapper environmentMapper;
    private final ServiceEnvironmentMapper serviceEnvironmentMapper;
    private final ProjectService projectService;

    public EnvironmentServiceImpl(
            EnvironmentMapper environmentMapper,
            ServiceEnvironmentMapper serviceEnvironmentMapper,
            ProjectService projectService
    ) {
        this.environmentMapper = environmentMapper;
        this.serviceEnvironmentMapper = serviceEnvironmentMapper;
        this.projectService = projectService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnvironmentEntity> page(Long projectId, String keyword, String status, long current, long size) {
        requireProject(projectId);

        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<EnvironmentEntity> wrapper = baseProjectQuery(projectId);
        wrapper.and(StringUtils.hasText(keyword), query -> query
                        .like(EnvironmentEntity::getCode, keyword)
                        .or()
                        .like(EnvironmentEntity::getName, keyword)
                        .or()
                        .like(EnvironmentEntity::getNamespace, keyword))
                .eq(StringUtils.hasText(status), EnvironmentEntity::getStatus, status)
                .orderByDesc(EnvironmentEntity::getCreatedAt);

        return environmentMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    /**
     * 全局环境页交给联表查询完成权限范围内的筛选和分页，避免页面对每个项目发起一次请求。
     */
    @Override
    @Transactional(readOnly = true)
    public Page<EnvironmentProjectRow> pageAll(
            List<Long> projectIds,
            Long projectId,
            String keyword,
            String status,
            long current,
            long size
    ) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        return environmentMapper.selectProjectPage(new Page<>(safeCurrent, safeSize), projectIds, projectId, keyword, status);
    }

    @Override
    @Transactional(readOnly = true)
    public EnvironmentEntity get(Long projectId, Long id) {
        requireProject(projectId);
        return requireEnvironment(projectId, id);
    }

    @Override
    @Transactional
    public EnvironmentEntity create(Long projectId, CreateEnvironmentRequest request) {
        ProjectEntity project = requireProject(projectId);
        if (!ACTIVE.equals(project.getStatus())) {
            throw new IllegalArgumentException("已停用项目不能新增环境");
        }

        boolean exists = environmentMapper.selectCount(baseProjectQuery(projectId)
                .eq(EnvironmentEntity::getCode, request.code())) > 0;
        if (exists) {
            throw new DuplicateKeyException("当前项目下环境编码已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.setProjectId(projectId);
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setNamespace(request.namespace());
        entity.setClusterName(request.clusterName());
        entity.setStatus(ACTIVE);
        entity.setDescription(request.description());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(false);

        // 唯一索引是最终防线；前置查询用于返回更明确的业务提示。
        environmentMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public EnvironmentEntity update(Long projectId, Long id, UpdateEnvironmentRequest request) {
        requireProject(projectId);
        EnvironmentEntity entity = requireEnvironment(projectId, id);
        entity.setName(request.name());
        entity.setNamespace(request.namespace());
        entity.setClusterName(request.clusterName());
        entity.setDescription(request.description());
        entity.setUpdatedAt(LocalDateTime.now());
        environmentMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public EnvironmentEntity updateStatus(Long projectId, Long id, UpdateEnvironmentStatusRequest request) {
        requireProject(projectId);
        EnvironmentEntity entity = requireEnvironment(projectId, id);
        String targetStatus = request.status();
        if (!ACTIVE.equals(targetStatus) && !DISABLED.equals(targetStatus)) {
            throw new IllegalArgumentException("环境状态只能是 ACTIVE 或 DISABLED");
        }

        // 重复提交相同状态保持幂等，避免产生无意义的数据库更新。
        if (targetStatus.equals(entity.getStatus())) {
            return entity;
        }

        entity.setStatus(targetStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        environmentMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long id) {
        requireProject(projectId);
        EnvironmentEntity entity = requireEnvironment(projectId, id);
        if (ACTIVE.equals(entity.getStatus())) {
            throw new IllegalArgumentException("启用中的环境不能删除，请先停用环境");
        }

        long bindingCount = serviceEnvironmentMapper.selectCount(new LambdaQueryWrapper<ServiceEnvironmentEntity>()
                .eq(ServiceEnvironmentEntity::getProjectId, projectId)
                .eq(ServiceEnvironmentEntity::getEnvironmentId, id));
        if (bindingCount > 0) {
            throw new IllegalArgumentException("环境仍关联微服务，请先解除微服务关联");
        }

        // 先记录更新时间，再执行逻辑删除；两次写操作处于同一个事务中。
        entity.setUpdatedAt(LocalDateTime.now());
        environmentMapper.updateById(entity);
        environmentMapper.deleteById(entity.getId());
    }

    /**
     * 全量完成状态和关联校验后再统一逻辑删除，避免前端批量操作出现部分删除。
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        List<EnvironmentEntity> environments = environmentMapper.selectByIds(ids);
        if (environments.size() != ids.size()) {
            throw new ResourceNotFoundException("部分环境不存在或已删除");
        }
        if (environments.stream().anyMatch(environment -> ACTIVE.equals(environment.getStatus()))) {
            throw new IllegalArgumentException("选中环境包含启用状态数据，请先全部停用");
        }

        long bindingCount = serviceEnvironmentMapper.selectCount(new LambdaQueryWrapper<ServiceEnvironmentEntity>()
                .in(ServiceEnvironmentEntity::getEnvironmentId, ids));
        if (bindingCount > 0) {
            throw new IllegalArgumentException("选中环境仍关联微服务，请先解除全部关联");
        }

        LocalDateTime now = LocalDateTime.now();
        environmentMapper.update(null, new LambdaUpdateWrapper<EnvironmentEntity>()
                .in(EnvironmentEntity::getId, ids)
                .set(EnvironmentEntity::getUpdatedAt, now));
        environmentMapper.deleteByIds(ids);
    }

    private ProjectEntity requireProject(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("项目 ID 必须是正整数");
        }
        return projectService.get(projectId);
    }

    private EnvironmentEntity requireEnvironment(Long projectId, Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("环境 ID 必须是正整数");
        }

        EnvironmentEntity entity = environmentMapper.selectOne(baseProjectQuery(projectId)
                .eq(EnvironmentEntity::getId, id));
        if (entity == null) {
            throw new ResourceNotFoundException("环境不存在");
        }
        return entity;
    }

    private LambdaQueryWrapper<EnvironmentEntity> baseProjectQuery(Long projectId) {
        return new LambdaQueryWrapper<EnvironmentEntity>()
                .eq(EnvironmentEntity::getProjectId, projectId);
    }
}
