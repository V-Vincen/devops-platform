package com.vincent.devops.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员创建平台用户请求。
 */
public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9._-]{2,63}$", message = "用户名格式不正确")
        String username,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 64, message = "显示名称不能超过64个字符")
        String displayName,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度必须在8到128个字符之间")
        String password,

        @NotBlank(message = "用户角色不能为空")
        @Pattern(regexp = "^(ADMIN|DEVELOPER)$", message = "用户角色只能是 ADMIN 或 DEVELOPER")
        String role
) {
}
