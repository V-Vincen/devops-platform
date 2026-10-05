package com.vincent.devops.auth.service;

import com.vincent.devops.auth.dto.CreateUserRequest;
import com.vincent.devops.auth.dto.UserResponse;

import java.util.List;

/**
 * 管理员用户和项目授权业务接口。
 */
public interface UserAdminService {

    /**
     * 查询平台用户列表，不返回密码摘要。
     */
    List<UserResponse> listUsers();

    /**
     * 查询开发人员已被授权访问的项目编号。
     * 管理员拥有全局访问权限，不维护逐项目授权记录，因此返回空列表。
     *
     * @param userId 用户主键
     * @return 已授权项目编号集合
     */
    List<String> listGrantedProjectIds(Long userId);

    /**
     * 创建平台用户。
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * 为用户授权项目访问权限。
     */
    void bindProject(Long userId, Long projectId);

    /**
     * 解除用户的项目访问权限。
     */
    void unbindProject(Long userId, Long projectId);
}
