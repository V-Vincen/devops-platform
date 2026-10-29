package com.vincent.devops.releaseconfig.dto;

import java.util.List;

/**
 * 服务发布配置的聚合响应。
 *
 * <p>仓库地址、构建定义和环境策略必须一起读取，避免前端分别请求后展示出不同版本的配置。</p>
 */
public record ReleaseConfigurationResponse(
        String serviceId,
        String repositoryId,
        String repositoryKey,
        String provider,
        String cloneUrl,
        String defaultBranch,
        String credentialReference,
        String buildSpecId,
        String sourceRoot,
        String changePathRules,
        String buildWorkdir,
        String buildType,
        String buildCommand,
        String dockerfilePath,
        String dockerContextPath,
        String imageRepository,
        List<EnvironmentPolicyResponse> environmentPolicies
) {
}
