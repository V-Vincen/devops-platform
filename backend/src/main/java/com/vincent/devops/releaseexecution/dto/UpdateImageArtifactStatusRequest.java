package com.vincent.devops.releaseexecution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 镜像推送完成后回写制品状态。 */
public record UpdateImageArtifactStatusRequest(
        @NotBlank(message = "镜像状态不能为空")
        @Pattern(regexp = "PENDING|PUBLISHED|FAILED", message = "镜像状态不正确")
        String status
) {
}
