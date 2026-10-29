package com.vincent.devops.releaseconfig.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 修改现有服务的发布配置。
 *
 * <p>凭据字段只接收 Jenkins 或平台受保护配置的引用名称，禁止传入访问令牌、密码或私钥。</p>
 */
public record UpdateReleaseConfigurationRequest(
        @NotBlank(message = "仓库标识不能为空") @Size(max = 128, message = "仓库标识不能超过128个字符") String repositoryKey,
        @NotBlank(message = "代码平台不能为空") @Pattern(regexp = "^(GITHUB|GITEE)$", message = "代码平台只能是 GITHUB 或 GITEE") String provider,
        @NotBlank(message = "Git 克隆地址不能为空") @Size(max = 500, message = "Git 克隆地址不能超过500个字符") String cloneUrl,
        @NotBlank(message = "默认分支不能为空") @Size(max = 128, message = "默认分支不能超过128个字符") String defaultBranch,
        @Size(max = 128, message = "凭据引用不能超过128个字符") String credentialReference,
        @NotBlank(message = "源码根目录不能为空") @Size(max = 500, message = "源码根目录不能超过500个字符") String sourceRoot,
        @NotBlank(message = "变更路径不能为空") @Size(max = 4000, message = "变更路径不能超过4000个字符") String changePathRules,
        @NotBlank(message = "构建工作目录不能为空") @Size(max = 500, message = "构建工作目录不能超过500个字符") String buildWorkdir,
        @NotBlank(message = "构建类型不能为空") @Pattern(regexp = "^[A-Z][A-Z0-9_-]{1,31}$", message = "构建类型格式不正确") String buildType,
        @Size(max = 1000, message = "构建命令不能超过1000个字符") String buildCommand,
        @NotBlank(message = "Dockerfile 路径不能为空") @Size(max = 500, message = "Dockerfile 路径不能超过500个字符") String dockerfilePath,
        @NotBlank(message = "Docker 构建上下文不能为空") @Size(max = 500, message = "Docker 构建上下文不能超过500个字符") String dockerContextPath,
        @NotBlank(message = "镜像仓库不能为空") @Size(max = 500, message = "镜像仓库不能超过500个字符") String imageRepository,
        @NotEmpty(message = "至少配置一个已关联环境") List<@Valid EnvironmentPolicyRequest> environmentPolicies
) {
    public record EnvironmentPolicyRequest(
            @NotBlank(message = "环境 ID 不能为空") @Pattern(regexp = "^[0-9]+$", message = "环境 ID 格式不正确") String environmentId,
            @NotBlank(message = "发布策略不能为空") @Pattern(regexp = "^(MANUAL_ONLY|AUTO_BUILD_MANUAL_DEPLOY|AUTO_BUILD_AUTO_DEPLOY)$", message = "发布策略不合法") String releasePolicy,
            boolean approvalRequired
    ) {
    }
}
