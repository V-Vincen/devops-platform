package com.vincent.devops.releaseexecution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 部署执行器回写部署状态。 */
public record UpdateDeploymentStatusRequest(
        @NotBlank(message = "部署状态不能为空")
        @Pattern(regexp = "PENDING|RUNNING|SUCCESS|FAILED|CANCELED", message = "部署状态不正确")
        String status,

        @Positive(message = "Helm Revision 必须是正整数")
        Integer helmRevision,

        @Size(max = 2000, message = "失败摘要不能超过2000个字符")
        String failureSummary
) {
}
