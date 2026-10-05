package com.vincent.devops.microservice.controller;

import com.vincent.devops.common.ApiResponse;
import com.vincent.devops.common.BatchDeleteRequest;
import com.vincent.devops.common.PageResponse;
import com.vincent.devops.auth.security.ProjectAccessService;
import com.vincent.devops.microservice.dto.EnvironmentOptionResponse;
import com.vincent.devops.microservice.dto.MicroserviceEnvironmentResponse;
import com.vincent.devops.microservice.service.MicroserviceService;
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
 * 跨项目微服务的批量管理接口。
 *
 * <p>批量删除只允许管理员调用，服务层在单一事务中检查状态和环境关联，
 * 任何一条数据不满足删除条件都会阻止整批删除。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/microservices")
public class MicroserviceBatchController {

    private final MicroserviceService microserviceService;
    private final ProjectAccessService projectAccessService;

    public MicroserviceBatchController(
            MicroserviceService microserviceService,
            ProjectAccessService projectAccessService
    ) {
        this.microserviceService = microserviceService;
        this.projectAccessService = projectAccessService;
    }

    /**
     * 分页查询全部可访问微服务及其环境关联。
     *
     * <p>同一微服务关联多个环境时会显示为多行，以便按“项目 → 环境”合并展示。
     * 未关联环境的历史微服务也会返回一行，环境字段为空。</p>
     */
    @GetMapping("/environment-bindings")
    public ApiResponse<PageResponse<MicroserviceEnvironmentResponse>> pageEnvironmentBindings(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            Authentication authentication
    ) {
        List<Long> accessibleProjectIds = (!projectAccessService.securityEnabled() || projectAccessService.isAdmin(authentication))
                ? null
                : projectAccessService.accessibleProjectIds(authentication);
        var page = microserviceService.pageEnvironmentBindings(
                accessibleProjectIds, projectId, environmentId, keyword, status, current, size
        );
        return ApiResponse.ok(PageResponse.from(page, MicroserviceEnvironmentResponse::from));
    }

    /**
     * 查询新增微服务时可选择的环境，选项同时包含项目与环境标识。
     */
    @GetMapping("/environment-options")
    public ApiResponse<List<EnvironmentOptionResponse>> environmentOptions(Authentication authentication) {
        List<Long> accessibleProjectIds = (!projectAccessService.securityEnabled() || projectAccessService.isAdmin(authentication))
                ? null
                : projectAccessService.accessibleProjectIds(authentication);
        return ApiResponse.ok(microserviceService.listAvailableEnvironments(accessibleProjectIds));
    }

    /**
     * 原子逻辑删除多个已停用微服务，并同步删除这些服务的全部环境关联。
     *
     * @param request 待删除微服务编号集合，单次最多 100 条
     * @return 空数据成功响应
     */
    @DeleteMapping("/batch")
    @PreAuthorize("@projectAccessService.canDeleteAll(authentication)")
    public ApiResponse<Void> deleteBatch(@Valid @RequestBody BatchDeleteRequest request) {
        microserviceService.deleteBatch(request.uniqueIds());
        return ApiResponse.ok(null);
    }
}
