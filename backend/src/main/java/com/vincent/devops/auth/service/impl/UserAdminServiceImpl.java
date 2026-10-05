package com.vincent.devops.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vincent.devops.auth.domain.UserEntity;
import com.vincent.devops.auth.domain.UserProjectEntity;
import com.vincent.devops.auth.dto.CreateUserRequest;
import com.vincent.devops.auth.dto.UserResponse;
import com.vincent.devops.auth.mapper.UserMapper;
import com.vincent.devops.auth.mapper.UserProjectMapper;
import com.vincent.devops.auth.service.UserAdminService;
import com.vincent.devops.common.ResourceNotFoundException;
import com.vincent.devops.project.service.ProjectService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员用户和项目授权业务实现。
 */
@Service
public class UserAdminServiceImpl implements UserAdminService {

    private final UserMapper userMapper;
    private final UserProjectMapper userProjectMapper;
    private final PasswordEncoder passwordEncoder;
    private final ProjectService projectService;

    public UserAdminServiceImpl(
            UserMapper userMapper,
            UserProjectMapper userProjectMapper,
            PasswordEncoder passwordEncoder,
            ProjectService projectService
    ) {
        this.userMapper = userMapper;
        this.userProjectMapper = userProjectMapper;
        this.passwordEncoder = passwordEncoder;
        this.projectService = projectService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .orderByAsc(UserEntity::getUsername))
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * 仅开发人员需要维护显式的项目访问权限；管理员由鉴权规则直接授予全局访问能力。
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> listGrantedProjectIds(Long userId) {
        UserEntity user = requireUser(userId);
        if (!"DEVELOPER".equals(user.getRole())) {
            return List.of();
        }

        return userProjectMapper.selectList(new LambdaQueryWrapper<UserProjectEntity>()
                        .eq(UserProjectEntity::getUserId, userId)
                        .orderByAsc(UserProjectEntity::getProjectId))
                .stream()
                .map(grant -> String.valueOf(grant.getProjectId()))
                .toList();
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        boolean exists = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username())) > 0;
        if (exists) {
            throw new DuplicateKeyException("用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleted(false);
        userMapper.insert(user);
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public void bindProject(Long userId, Long projectId) {
        UserEntity user = requireUser(userId);
        projectService.get(projectId);
        if (!"DEVELOPER".equals(user.getRole())) {
            throw new IllegalArgumentException("只有开发人员需要配置项目访问授权");
        }

        boolean exists = userProjectMapper.selectCount(new LambdaQueryWrapper<UserProjectEntity>()
                .eq(UserProjectEntity::getUserId, userId)
                .eq(UserProjectEntity::getProjectId, projectId)) > 0;
        if (exists) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        UserProjectEntity relation = new UserProjectEntity();
        relation.setUserId(userId);
        relation.setProjectId(projectId);
        relation.setCreatedAt(now);
        relation.setUpdatedAt(now);
        relation.setDeleted(false);
        userProjectMapper.insert(relation);
    }

    @Override
    @Transactional
    public void unbindProject(Long userId, Long projectId) {
        requireUser(userId);
        projectService.get(projectId);
        UserProjectEntity relation = userProjectMapper.selectOne(new LambdaQueryWrapper<UserProjectEntity>()
                .eq(UserProjectEntity::getUserId, userId)
                .eq(UserProjectEntity::getProjectId, projectId));
        if (relation == null) {
            throw new ResourceNotFoundException("用户项目授权不存在");
        }
        userProjectMapper.deleteById(relation.getId());
    }

    private UserEntity requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 必须是正整数");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        return user;
    }
}
