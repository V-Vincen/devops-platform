package com.vincent.devops.releaseexecution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Jenkins 或其他执行器回写构建状态时使用的请求。 */
public record UpdatePipelineRunStatusRequest(
        @NotBlank(message = "构建状态不能为空")
        @Pattern(regexp = "PENDING|RUNNING|SUCCESS|FAILED|CANCELED", message = "构建状态不正确")
        String status,

        @Size(max = 255, message = "Jenkins Job 名称不能超过255个字符")
        String jenkinsJobName,

        @Positive(message = "Jenkins 构建编号必须是正整数")
        Long jenkinsBuildNumber,

        @Size(max = 1000, message = "Jenkins 构建地址不能超过1000个字符")
        String jenkinsBuildUrl,

        @Size(max = 2000, message = "失败摘要不能超过2000个字符")
        String failureSummary
) {
}
