package com.vincent.devops.releaseexecution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建镜像制品记录；镜像 Digest 是部署和回滚的不可变依据。 */
public record CreateImageArtifactRequest(
        @NotBlank(message = "镜像仓库不能为空")
        @Size(max = 500, message = "镜像仓库地址不能超过500个字符")
        String imageRepository,

        @NotBlank(message = "镜像标签不能为空")
        @Size(max = 255, message = "镜像标签不能超过255个字符")
        String imageTag,

        @NotBlank(message = "镜像 Digest 不能为空")
        @Pattern(regexp = "^sha256:[0-9a-fA-F]{64}$", message = "镜像 Digest 必须是 sha256 加64位十六进制字符")
        String imageDigest
) {
}
