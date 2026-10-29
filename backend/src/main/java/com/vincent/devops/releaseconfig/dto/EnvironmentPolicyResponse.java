package com.vincent.devops.releaseconfig.dto;

/** 服务在一个已关联环境中的发布策略。 */
public record EnvironmentPolicyResponse(
        String environmentId,
        String environmentCode,
        String environmentName,
        String releasePolicy,
        boolean approvalRequired,
        String status
) {
}
