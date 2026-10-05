package com.vincent.devops.auth.dto;

import java.time.LocalDateTime;

/**
 * 登录成功响应。
 *
 * @param accessToken 后续请求使用的访问令牌
 * @param tokenType 令牌类型，固定为 Bearer
 * @param expiresAt 令牌过期时间
 * @param user 当前用户信息
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        LocalDateTime expiresAt,
        UserResponse user
) {
}
