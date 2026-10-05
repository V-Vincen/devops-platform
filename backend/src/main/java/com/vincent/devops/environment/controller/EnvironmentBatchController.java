package com.vincent.devops.environment.controller;

import com.vincent.devops.common.ApiResponse;
import com.vincent.devops.common.BatchDeleteRequest;
import com.vincent.devops.common.PageResponse;
import com.vincent.devops.auth.security.ProjectAccessService;
import com.vincent.devops.environment.dto.EnvironmentProjectResponse;
import com.vincent.devops.environment.dto.EnvironmentProjectRow;
import com.vincent.devops.environment.service.EnvironmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * 跨项目环境的批量管理接口。
 *
 * <p>环境列表按项目分组展示时，前端可能一次选中多个项目组，因此批量删除使用独立根路径，
 * 由服务层校验每条环境的状态和微服务关联。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/environments")
public class EnvironmentBatchController {

    private final EnvironmentService environmentService;
    private final ProjectAccessService projectAccessService;

    public EnvironmentBatchController(
            EnvironmentService environmentService,
            ProjectAccessService projectAccessService
    ) {
        this.environmentService = environmentService;
        this.projectAccessService = projectAccessService;
    }

    /**
     * 分页查询全部可访问环境，响应包含项目名称和编码以支持页面按项目合并展示。
     *
     * <p>可选项目条件只会在当前用户可访问范围内继续收窄结果；开发人员无法通过修改参数查看未授权项目。</p>
     */
    @GetMapping
    public ApiResponse<PageResponse<EnvironmentProjectResponse>> page(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            Authentication authentication
    ) {
        List<Long> projectIds = (!projectAccessService.securityEnabled() || projectAccessService.isAdmin(authentication))
                ? null
                : projectAccessService.accessibleProjectIds(authentication);
        var page = environmentService.pageAll(projectIds, projectId, keyword, status, current, size);
        return ApiResponse.ok(PageResponse.from(page, EnvironmentProjectResponse::from));
    }

    /**
     * 原子逻辑删除多个已停用且未关联微服务的环境。
     *
     * @param request 待删除环境编号集合，单次最多 100 条
     * @return 空数据成功响应
     */
    @DeleteMapping("/batch")
    @PreAuthorize("@projectAccessService.canDeleteAll(authentication)")
    public ApiResponse<Void> deleteBatch(@Valid @RequestBody BatchDeleteRequest request) {
        environmentService.deleteBatch(request.uniqueIds());
        return ApiResponse.ok(null);
    }
}
