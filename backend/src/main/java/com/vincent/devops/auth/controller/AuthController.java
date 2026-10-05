package com.vincent.devops.auth.controller;

import com.vincent.devops.auth.dto.LoginRequest;
import com.vincent.devops.auth.dto.LoginResponse;
import com.vincent.devops.auth.dto.UserResponse;
import com.vincent.devops.auth.security.AuthTokenStore;
import com.vincent.devops.auth.security.DevopsUserPrincipal;
import com.vincent.devops.auth.service.AuthService;
import com.vincent.devops.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础登录接口。
 *
 * <p>登录成功后返回 Bearer 令牌，前端后续请求需要通过
 * {@code Authorization: Bearer &lt;token&gt;} 传递。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthTokenStore tokenStore;

    public AuthController(AuthService authService, AuthTokenStore tokenStore) {
        this.authService = authService;
        this.tokenStore = tokenStore;
    }

    /**
     * 用户登录。
     *
     * @param request 用户名和密码
     * @return 访问令牌和当前用户信息
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * 查询当前登录用户。
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> current(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof DevopsUserPrincipal principal)) {
            throw new BadCredentialsException("登录状态已失效");
        }
        return ApiResponse.ok(authService.currentUser(principal));
    }

    /**
     * 注销当前令牌。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        tokenStore.remove(resolveToken(authorization));
        return ApiResponse.ok(null);
    }

    private String resolveToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
