package com.vincent.devops.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vincent.devops.auth.domain.UserEntity;
import com.vincent.devops.auth.dto.LoginRequest;
import com.vincent.devops.auth.dto.LoginResponse;
import com.vincent.devops.auth.dto.UserResponse;
import com.vincent.devops.auth.mapper.UserMapper;
import com.vincent.devops.auth.security.AuthTokenStore;
import com.vincent.devops.auth.security.DevopsUserPrincipal;
import com.vincent.devops.auth.service.AuthService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录业务实现。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final String ACTIVE = "ACTIVE";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenStore tokenStore;

    public AuthServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            AuthTokenStore tokenStore
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username()));
        // 用户不存在、密码错误和账号停用统一返回相同错误，避免暴露账号状态。
        if (user == null || !ACTIVE.equals(user.getStatus())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        AuthTokenStore.TokenSession session = tokenStore.create(user);
        return new LoginResponse(
                session.token(),
                "Bearer",
                session.expiresAt(),
                UserResponse.from(user)
        );
    }

    @Override
    public UserResponse currentUser(DevopsUserPrincipal principal) {
        if (principal == null) {
            throw new BadCredentialsException("登录状态已失效");
        }
        UserEntity user = userMapper.selectById(principal.userId());
        if (user == null || !ACTIVE.equals(user.getStatus())) {
            throw new BadCredentialsException("登录状态已失效");
        }
        return UserResponse.from(user);
    }
}
