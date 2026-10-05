package com.vincent.devops.common;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理后台批量逻辑删除的统一请求参数。
 *
 * <p>单次最多处理 100 条，避免一次事务占用过多行锁；重复编号会被拒绝，
 * 防止调用方误以为同一资源可被重复删除。</p>
 *
 * @param ids 待删除资源的主键集合
 */
public record BatchDeleteRequest(
        @NotEmpty(message = "至少选择一条数据")
        @Size(max = 100, message = "单次最多删除 100 条数据")
        List<@NotNull(message = "资源 ID 不能为空") @Positive(message = "资源 ID 必须是正整数") Long> ids
) {

    /**
     * 返回经过重复校验的编号集合，供业务层作为唯一删除范围使用。
     */
    public List<Long> uniqueIds() {
        List<Long> uniqueIds = ids.stream().distinct().toList();
        if (uniqueIds.size() != ids.size()) {
            throw new IllegalArgumentException("批量删除数据不能包含重复 ID");
        }
        return uniqueIds;
    }
}
