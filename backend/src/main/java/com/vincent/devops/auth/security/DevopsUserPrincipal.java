package com.vincent.devops.auth.security;

/**
 * 放入 Spring Security 上下文的最小用户信息。
 */
public record DevopsUserPrincipal(
        Long userId,
        String username,
        String displayName,
        String role
) {
}
