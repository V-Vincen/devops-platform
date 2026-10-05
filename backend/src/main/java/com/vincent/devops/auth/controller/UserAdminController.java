package com.vincent.devops.auth.controller;

import com.vincent.devops.auth.dto.CreateUserRequest;
import com.vincent.devops.auth.dto.UserResponse;
import com.vincent.devops.auth.service.UserAdminService;
import com.vincent.devops.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员用户和项目授权接口。
 *
 * <p>接口路径由安全配置限制为 ADMIN 角色。</p>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * 查询平台用户列表。
     */
    @GetMapping
    public ApiResponse<List<UserResponse>> listUsers() {
        return ApiResponse.ok(userAdminService.listUsers());
    }

    /**
     * 查询指定开发人员已获得访问权限的项目，用于权限管理页面回显。
     * 管理员角色默认拥有全部项目权限，接口返回空集合。
     *
     * @param userId 用户主键
     * @return 已授权项目编号集合
     */
    @GetMapping("/{userId}/projects")
    public ApiResponse<List<String>> listGrantedProjectIds(@PathVariable Long userId) {
        return ApiResponse.ok(userAdminService.listGrantedProjectIds(userId));
    }

    /**
     * 创建管理员或开发人员账号。
     */
    @PostMapping
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userAdminService.createUser(request));
    }

    /**
     * 给开发人员授权项目访问权限，重复授权保持幂等。
     */
    @PostMapping("/{userId}/projects/{projectId}")
    public ApiResponse<Void> bindProject(
            @PathVariable Long userId,
            @PathVariable Long projectId
    ) {
        userAdminService.bindProject(userId, projectId);
        return ApiResponse.ok(null);
    }

    /**
     * 解除开发人员的项目访问权限。
     */
    @DeleteMapping("/{userId}/projects/{projectId}")
    public ApiResponse<Void> unbindProject(
            @PathVariable Long userId,
            @PathVariable Long projectId
    ) {
        userAdminService.unbindProject(userId, projectId);
        return ApiResponse.ok(null);
    }
}
