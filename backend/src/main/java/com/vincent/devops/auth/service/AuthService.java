package com.vincent.devops.auth.service;

import com.vincent.devops.auth.dto.LoginRequest;
import com.vincent.devops.auth.dto.LoginResponse;
import com.vincent.devops.auth.dto.UserResponse;
import com.vincent.devops.auth.security.DevopsUserPrincipal;

/**
 * 登录和当前用户业务接口。
 */
public interface AuthService {

    /**
     * 校验用户名密码并创建访问令牌。
     */
    LoginResponse login(LoginRequest request);

    /**
     * 将当前认证主体转换为前端用户信息。
     */
    UserResponse currentUser(DevopsUserPrincipal principal);
}
