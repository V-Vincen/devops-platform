package com.vincent.devops.microservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建微服务请求。
 *
 * @param serviceCode 项目内唯一的服务编码
 * @param serviceName 服务名称
 * @param repositoryUrl Gitee 等代码仓库地址
 * @param branchName 构建分支
 * @param buildType 构建类型，例如 MAVEN、NODE_JS
 * @param imageRepository Docker 镜像仓库地址
 * @param port 容器服务端口
 * @param replicas 默认副本数量
 * @param cpuLimit CPU 资源上限，可为空
 * @param memoryLimit 内存资源上限，可为空
 * @param description 服务描述
 * @param environmentIds 创建后关联的同项目环境，可为空以兼容历史接口调用
 */
public record CreateMicroserviceRequest(
        @NotBlank(message = "微服务编码不能为空")
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$", message = "微服务编码只能使用小写字母、数字和短横线")
        String serviceCode,

        @NotBlank(message = "微服务名称不能为空")
        @Size(max = 128, message = "微服务名称不能超过128个字符")
        String serviceName,

        @NotBlank(message = "代码仓库地址不能为空")
        @Size(max = 500, message = "代码仓库地址不能超过500个字符")
        String repositoryUrl,

        @NotBlank(message = "构建分支不能为空")
        @Size(max = 128, message = "构建分支不能超过128个字符")
        String branchName,

        @NotBlank(message = "构建类型不能为空")
        @Pattern(regexp = "^[A-Z][A-Z0-9_-]{1,31}$", message = "构建类型格式不正确")
        String buildType,

        @NotBlank(message = "镜像仓库地址不能为空")
        @Size(max = 500, message = "镜像仓库地址不能超过500个字符")
        String imageRepository,

        @NotNull(message = "服务端口不能为空")
        @Min(value = 1, message = "服务端口必须在1到65535之间")
        @Max(value = 65535, message = "服务端口必须在1到65535之间")
        Integer port,

        @NotNull(message = "副本数量不能为空")
        @Min(value = 1, message = "副本数量必须在1到20之间")
        @Max(value = 20, message = "副本数量必须在1到20之间")
        Integer replicas,

        @Size(max = 32, message = "CPU 限制不能超过32个字符")
        String cpuLimit,

        @Size(max = 32, message = "内存限制不能超过32个字符")
        String memoryLimit,

        @Size(max = 500, message = "服务描述不能超过500个字符")
        String description,

        @Size(max = 100, message = "单次最多关联 100 个环境")
        List<@NotNull(message = "环境 ID 不能为空") @Positive(message = "环境 ID 必须是正整数") Long> environmentIds
) {

    /**
     * 创建接口兼容未传环境的历史调用；传入时拒绝重复值，保证关联范围明确。
     */
    public List<Long> uniqueEnvironmentIds() {
        if (environmentIds == null || environmentIds.isEmpty()) {
            return List.of();
        }
        List<Long> uniqueIds = environmentIds.stream().distinct().toList();
        if (uniqueIds.size() != environmentIds.size()) {
            throw new IllegalArgumentException("关联环境不能包含重复 ID");
        }
        return uniqueIds;
    }
}
