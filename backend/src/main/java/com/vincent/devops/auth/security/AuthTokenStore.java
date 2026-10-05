package com.vincent.devops.auth.security;

import com.vincent.devops.auth.domain.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例测试环境使用的内存令牌存储。
 *
 * <p>令牌不会写入数据库，服务重启后全部失效。多实例部署时应替换为 Redis
 * 或其他共享会话存储。</p>
 */
@Component
public class AuthTokenStore {

    private final ConcurrentHashMap<String, TokenSession> sessions = new ConcurrentHashMap<>();
    private final long expireHours;

    public AuthTokenStore(@Value("${devops.security.token-expire-hours:12}") long expireHours) {
        this.expireHours = Math.max(expireHours, 1);
    }

    /**
     * 为登录用户创建令牌。
     */
    public TokenSession create(UserEntity user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        TokenSession session = new TokenSession(
                token,
                new DevopsUserPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()),
                LocalDateTime.now().plusHours(expireHours)
        );
        sessions.put(token, session);
        return session;
    }

    /**
     * 获取尚未过期的令牌会话。
     */
    public Optional<TokenSession> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        TokenSession session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * 注销指定令牌。
     */
    public void remove(String token) {
        if (token != null && !token.isBlank()) {
            sessions.remove(token);
        }
    }

    /**
     * 令牌会话内部结构。
     */
    public record TokenSession(String token, DevopsUserPrincipal principal, LocalDateTime expiresAt) {
    }
}
