package com.vincent.devops.releaseconfig.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vincent.devops.common.ResourceNotFoundException;
import com.vincent.devops.releaseconfig.dto.EnvironmentPolicyResponse;
import com.vincent.devops.releaseconfig.dto.ReleaseConfigurationResponse;
import com.vincent.devops.releaseconfig.dto.ReleaseConfigurationRow;
import com.vincent.devops.releaseconfig.dto.UpdateReleaseConfigurationRequest;
import com.vincent.devops.releaseconfig.mapper.ReleaseConfigurationMapper;
import com.vincent.devops.releaseconfig.service.ReleaseConfigurationService;
import com.vincent.devops.project.service.ProjectService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 服务发布配置实现，仓库、构建定义和策略在一个事务中更新，避免部分配置生效。 */
@Service
public class ReleaseConfigurationServiceImpl implements ReleaseConfigurationService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ReleaseConfigurationMapper mapper;
    private final ProjectService projectService;

    public ReleaseConfigurationServiceImpl(ReleaseConfigurationMapper mapper, ProjectService projectService) {
        this.mapper = mapper;
        this.projectService = projectService;
    }

    @Override
    @Transactional(readOnly = true)
    public ReleaseConfigurationResponse get(Long projectId, Long serviceId) {
        projectService.get(projectId);
        ReleaseConfigurationRow configuration = mapper.selectConfiguration(projectId, serviceId);
        if (configuration == null) {
            throw new ResourceNotFoundException("未找到微服务或其发布配置");
        }
        return withPolicies(configuration, mapper.selectPolicies(projectId, serviceId));
    }

    @Override
    @Transactional
    public ReleaseConfigurationResponse update(Long projectId, Long serviceId, UpdateReleaseConfigurationRequest request) {
        ReleaseConfigurationResponse current = get(projectId, serviceId);
        validateChangePathRules(request.changePathRules());
        validatePolicyScope(current.environmentPolicies(), request.environmentPolicies());
        try {
            if (mapper.updateRepository(projectId, Long.valueOf(current.repositoryId()), request.repositoryKey(), request.provider(),
                    request.cloneUrl(), request.defaultBranch(), blankToNull(request.credentialReference())) != 1
                    || mapper.updateBuildSpec(projectId, serviceId, Long.valueOf(current.buildSpecId()), request.sourceRoot(),
                    request.changePathRules(), request.buildWorkdir(), request.buildType(), blankToNull(request.buildCommand()),
                    request.dockerfilePath(), request.dockerContextPath(), request.imageRepository()) != 1) {
                throw new ResourceNotFoundException("发布配置已不存在或已变更，请刷新后重试");
            }
            for (UpdateReleaseConfigurationRequest.EnvironmentPolicyRequest policy : request.environmentPolicies()) {
                if (mapper.updatePolicy(projectId, serviceId, Long.valueOf(policy.environmentId()), policy.releasePolicy(), policy.approvalRequired()) != 1) {
                    throw new IllegalArgumentException("只能修改当前微服务已关联环境的发布策略");
                }
            }
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("项目内仓库标识不能重复");
        }
        return get(projectId, serviceId);
    }

    private static void validatePolicyScope(List<EnvironmentPolicyResponse> existing,
                                            List<UpdateReleaseConfigurationRequest.EnvironmentPolicyRequest> requested) {
        Set<String> existingIds = new HashSet<>();
        existing.forEach(policy -> existingIds.add(policy.environmentId()));
        Set<String> requestedIds = new HashSet<>();
        for (UpdateReleaseConfigurationRequest.EnvironmentPolicyRequest policy : requested) {
            if (!requestedIds.add(policy.environmentId())) {
                throw new IllegalArgumentException("发布策略不能包含重复环境");
            }
        }
        if (!existingIds.equals(requestedIds)) {
            throw new IllegalArgumentException("发布策略必须覆盖且仅覆盖当前已关联环境");
        }
    }

    private static ReleaseConfigurationResponse withPolicies(ReleaseConfigurationRow configuration,
                                                              List<EnvironmentPolicyResponse> policies) {
        return new ReleaseConfigurationResponse(configuration.serviceId(), configuration.repositoryId(),
                configuration.repositoryKey(), configuration.provider(), configuration.cloneUrl(),
                configuration.defaultBranch(), configuration.credentialReference(), configuration.buildSpecId(),
                configuration.sourceRoot(), configuration.changePathRules(), configuration.buildWorkdir(),
                configuration.buildType(), configuration.buildCommand(), configuration.dockerfilePath(),
                configuration.dockerContextPath(), configuration.imageRepository(), policies);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 路径规则保存为 JSON 数组，先在服务层校验，避免数据库异常直接暴露给接口调用方。 */
    private static void validateChangePathRules(String value) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(value);
            if (root == null || !root.isArray() || root.isEmpty() || !root.valueStream().allMatch(JsonNode::isTextual)) {
                throw new IllegalArgumentException("变更路径规则必须是至少包含一个文本路径的 JSON 数组");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("变更路径规则必须是 JSON 数组");
        }
    }
}
