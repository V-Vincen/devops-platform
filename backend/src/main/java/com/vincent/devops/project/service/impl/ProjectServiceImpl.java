package com.vincent.devops.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.common.ResourceNotFoundException;
import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.mapper.EnvironmentMapper;
import com.vincent.devops.microservice.domain.MicroserviceEntity;
import com.vincent.devops.microservice.mapper.MicroserviceMapper;
import com.vincent.devops.project.domain.ProjectEntity;
import com.vincent.devops.project.dto.CreateProjectRequest;
import com.vincent.devops.project.dto.UpdateProjectRequest;
import com.vincent.devops.project.dto.UpdateProjectStatusRequest;
import com.vincent.devops.project.mapper.ProjectMapper;
import com.vincent.devops.project.service.ProjectService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectServiceImpl implements ProjectService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";

    private final ProjectMapper projectMapper;
    private final EnvironmentMapper environmentMapper;
    private final MicroserviceMapper microserviceMapper;

    public ProjectServiceImpl(
            ProjectMapper projectMapper,
            EnvironmentMapper environmentMapper,
            MicroserviceMapper microserviceMapper
    ) {
        this.projectMapper = projectMapper;
        this.environmentMapper = environmentMapper;
        this.microserviceMapper = microserviceMapper;
    }

    @Override
    public Page<ProjectEntity> page(String keyword, String status, long current, long size) {
        return pageInternal(null, keyword, status, current, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectEntity> pageByProjectIds(
            List<Long> projectIds,
            String keyword,
            String status,
            long current,
            long size
    ) {
        return pageInternal(projectIds, keyword, status, current, size);
    }

    private Page<ProjectEntity> pageInternal(
            List<Long> projectIds,
            String keyword,
            String status,
            long current,
            long size
    ) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<ProjectEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(keyword), query -> query
                        .like(ProjectEntity::getCode, keyword)
                        .or()
                        .like(ProjectEntity::getName, keyword))
                .eq(StringUtils.hasText(status), ProjectEntity::getStatus, status)
                .orderByDesc(ProjectEntity::getCreatedAt);

        if (projectIds != null) {
            if (projectIds.isEmpty()) {
                wrapper.in(ProjectEntity::getId, List.of(-1L));
            } else {
                wrapper.in(ProjectEntity::getId, projectIds);
            }
        }

        return projectMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectEntity get(Long id) {
        return requireProject(id);
    }

    @Override
    @Transactional
    public ProjectEntity create(CreateProjectRequest request) {
        boolean exists = projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getCode, request.code())) > 0;
        if (exists) {
            throw new DuplicateKeyException("项目编码已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        ProjectEntity entity = new ProjectEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus(ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(false);

        // 数据库唯一索引是最终防线；这里的前置查询只用于返回更友好的提示。
        projectMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public ProjectEntity update(Long id, UpdateProjectRequest request) {
        ProjectEntity entity = requireProject(id);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public ProjectEntity updateStatus(Long id, UpdateProjectStatusRequest request) {
        ProjectEntity entity = requireProject(id);
        String targetStatus = request.status();
        if (!ACTIVE.equals(targetStatus) && !DISABLED.equals(targetStatus)) {
            throw new IllegalArgumentException("项目状态只能是 ACTIVE 或 DISABLED");
        }

        // 重复提交相同状态是幂等操作，直接返回当前项目，避免产生无意义的数据库更新。
        if (targetStatus.equals(entity.getStatus())) {
            return entity;
        }

        entity.setStatus(targetStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ProjectEntity entity = requireProject(id);
        if (ACTIVE.equals(entity.getStatus())) {
            throw new IllegalArgumentException("启用中的项目不能删除，请先停用项目");
        }

        long environmentCount = environmentMapper.selectCount(new LambdaQueryWrapper<EnvironmentEntity>()
                .eq(EnvironmentEntity::getProjectId, id));
        long microserviceCount = microserviceMapper.selectCount(new LambdaQueryWrapper<MicroserviceEntity>()
                .eq(MicroserviceEntity::getProjectId, id));
        if (environmentCount > 0 || microserviceCount > 0) {
            throw new IllegalArgumentException("项目仍有关联环境或微服务，请先清理下级资源");
        }

        // 先保存更新时间，再执行 MyBatis-Plus 逻辑删除；整个过程处于同一个事务中。
        entity.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(entity);
        projectMapper.deleteById(entity.getId());
    }

    /**
     * 批量删除采用“先全量校验、再统一写入”流程，禁止在校验过程中提前删除任意项目。
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        List<ProjectEntity> projects = projectMapper.selectByIds(ids);
        if (projects.size() != ids.size()) {
            throw new ResourceNotFoundException("部分项目不存在或已删除");
        }
        if (projects.stream().anyMatch(project -> ACTIVE.equals(project.getStatus()))) {
            throw new IllegalArgumentException("选中项目包含启用状态数据，请先全部停用");
        }

        long environmentCount = environmentMapper.selectCount(new LambdaQueryWrapper<EnvironmentEntity>()
                .in(EnvironmentEntity::getProjectId, ids));
        long microserviceCount = microserviceMapper.selectCount(new LambdaQueryWrapper<MicroserviceEntity>()
                .in(MicroserviceEntity::getProjectId, ids));
        if (environmentCount > 0 || microserviceCount > 0) {
            throw new IllegalArgumentException("选中项目仍有关联环境或微服务，请先清理下级资源");
        }

        LocalDateTime now = LocalDateTime.now();
        projectMapper.update(null, new LambdaUpdateWrapper<ProjectEntity>()
                .in(ProjectEntity::getId, ids)
                .set(ProjectEntity::getUpdatedAt, now));
        projectMapper.deleteByIds(ids);
    }

    private ProjectEntity requireProject(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("项目 ID 必须是正整数");
        }

        ProjectEntity entity = projectMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("项目不存在");
        }
        return entity;
    }
}
