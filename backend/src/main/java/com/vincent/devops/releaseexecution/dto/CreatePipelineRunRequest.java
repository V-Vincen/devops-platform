package com.vincent.devops.releaseexecution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 创建构建执行记录；参数快照只允许保存不含密钥的 JSON 对象。 */
public record CreatePipelineRunRequest(
        @NotNull(message = "目标环境不能为空")
        @Positive(message = "目标环境 ID 必须是正整数")
        Long environmentId,

        @NotBlank(message = "触发类型不能为空")
        @Pattern(regexp = "MANUAL|WEBHOOK|RETRY", message = "触发类型只能是 MANUAL、WEBHOOK 或 RETRY")
        String triggerType,

        @Size(max = 255, message = "外部事件 ID 不能超过255个字符")
        String externalEventId,

        @NotBlank(message = "Git 分支或标签不能为空")
        @Size(max = 255, message = "Git 分支或标签不能超过255个字符")
        String gitRef,

        @Size(max = 128, message = "提交号不能超过128个字符")
        String commitSha,

        @Size(max = 10000, message = "执行参数快照不能超过10000个字符")
        String parameterSnapshot
) {

    public String normalizedParameterSnapshot() {
        return parameterSnapshot == null || parameterSnapshot.isBlank() ? "{}" : parameterSnapshot.trim();
    }

    public String normalizedExternalEventId() {
        return externalEventId == null || externalEventId.isBlank() ? null : externalEventId.trim();
    }

    public String normalizedCommitSha() {
        return commitSha == null || commitSha.isBlank() ? null : commitSha.trim();
    }
}
