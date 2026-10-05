package com.vincent.devops.auth.dto;

import com.vincent.devops.auth.domain.UserEntity;

/**
 * 面向前端的用户信息，不返回密码摘要。
 */
public record UserResponse(
        String id,
        String username,
        String displayName,
        String role,
        String status
) {

    public static UserResponse from(UserEntity entity) {
        return new UserResponse(
                String.valueOf(entity.getId()),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getRole(),
                entity.getStatus()
        );
    }
}
