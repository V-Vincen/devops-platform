package com.vincent.devops.microservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 为一个微服务批量关联环境的请求参数。
 *
 * <p>拒绝重复环境编号，避免调用方误以为一条关系会被创建多次。</p>
 */
public record BindMicroserviceEnvironmentsRequest(
        @NotEmpty(message = "至少选择一个关联环境")
        @Size(max = 100, message = "单次最多关联 100 个环境")
        List<@NotNull(message = "环境 ID 不能为空") @Positive(message = "环境 ID 必须是正整数") Long> environmentIds
) {

    public List<Long> uniqueEnvironmentIds() {
        List<Long> uniqueIds = environmentIds.stream().distinct().toList();
        if (uniqueIds.size() != environmentIds.size()) {
            throw new IllegalArgumentException("关联环境不能包含重复 ID");
        }
        return uniqueIds;
    }
}
