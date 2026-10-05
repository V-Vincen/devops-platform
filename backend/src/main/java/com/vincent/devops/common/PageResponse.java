package com.vincent.devops.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 对前端稳定暴露的分页结构，避免直接暴露 MyBatis-Plus 的分页对象。
 */
public record PageResponse<T>(
        List<T> records,
        long current,
        long size,
        long total,
        long pages
) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getRecords().stream().map(mapper).toList(),
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages()
        );
    }
}
