package com.vincent.devops.microservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改微服务请求。
 *
 * <p>微服务编码创建后不可修改。</p>
 */
public record UpdateMicroserviceRequest(
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
        String description
) {
}
