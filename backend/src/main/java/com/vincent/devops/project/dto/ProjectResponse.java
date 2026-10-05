package com.vincent.devops.project.dto;

import com.vincent.devops.project.domain.ProjectEntity;

import java.time.LocalDateTime;

/**
 * 面向前端的项目响应对象。
 *
 * @param id 项目 ID，使用字符串避免前端处理 64 位主键时发生精度丢失
 * @param code 项目编码
 * @param name 项目名称
 * @param description 项目描述
 * @param status 项目状态
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ProjectResponse(
        String id,
        String code,
        String name,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProjectResponse from(ProjectEntity entity) {
        return new ProjectResponse(
                String.valueOf(entity.getId()),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
