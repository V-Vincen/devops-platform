package com.vincent.devops.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vincent.devops.auth.domain.UserProjectEntity;
import com.vincent.devops.auth.mapper.UserProjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 项目访问权限判断器。
 *
 * <p>管理员默认访问全部项目；开发人员必须存在有效的用户项目授权记录。
 * 本地关闭安全开关时，保留原有接口的开放行为，便于排查启动问题。</p>
 */
@Component("projectAccessService")
public class ProjectAccessService {

    private final UserProjectMapper userProjectMapper;
    private final boolean securityEnabled;

    public ProjectAccessService(
            UserProjectMapper userProjectMapper,
            @Value("${devops.security.enabled:true}") boolean securityEnabled
    ) {
        this.userProjectMapper = userProjectMapper;
        this.securityEnabled = securityEnabled;
    }

    /**
     * 判断当前用户是否可以查看指定项目。
     */
    public boolean canRead(Authentication authentication, Long projectId) {
        if (!securityEnabled) {
            return true;
        }
        DevopsUserPrincipal principal = principal(authentication);
        if (principal == null || projectId == null || projectId <= 0) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        return userProjectMapper.selectCount(new LambdaQueryWrapper<UserProjectEntity>()
                .eq(UserProjectEntity::getUserId, principal.userId())
                .eq(UserProjectEntity::getProjectId, projectId)) > 0;
    }

    /**
     * 判断当前用户是否可以操作项目下的环境和微服务。
     */
    public boolean canOperate(Authentication authentication, Long projectId) {
        return canRead(authentication, projectId);
    }

    /**
     * 只有管理员可以创建项目。
     */
    public boolean canCreate(Authentication authentication) {
        return !securityEnabled || isAdmin(authentication);
    }

    /**
     * 删除项目、环境或微服务需要管理员权限。
     */
    public boolean canDelete(Authentication authentication, Long projectId) {
        return !securityEnabled || (isAdmin(authentication) && canRead(authentication, projectId));
    }

    /**
     * 批量删除接口只对管理员开放。
     * 资源归属和状态由各业务服务在同一事务内继续校验，避免仅依赖前端选中结果。
     */
    public boolean canDeleteAll(Authentication authentication) {
        return !securityEnabled || isAdmin(authentication);
    }

    /**
     * 当前请求是否为管理员。
     */
    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    /**
     * 返回开发人员可以访问的项目 ID。
     */
    public List<Long> accessibleProjectIds(Authentication authentication) {
        DevopsUserPrincipal principal = principal(authentication);
        if (principal == null) {
            return List.of();
        }
        return userProjectMapper.selectList(new LambdaQueryWrapper<UserProjectEntity>()
                        .eq(UserProjectEntity::getUserId, principal.userId())
                        .select(UserProjectEntity::getProjectId))
                .stream()
                .map(UserProjectEntity::getProjectId)
                .toList();
    }

    /**
     * 安全开关当前值，供项目列表决定是否应用授权过滤。
     */
    public boolean securityEnabled() {
        return securityEnabled;
    }

    private DevopsUserPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof DevopsUserPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
