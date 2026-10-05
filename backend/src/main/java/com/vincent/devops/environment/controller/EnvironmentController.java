package com.vincent.devops.environment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.common.ApiResponse;
import com.vincent.devops.common.PageResponse;
import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.dto.CreateEnvironmentRequest;
import com.vincent.devops.environment.dto.EnvironmentResponse;
import com.vincent.devops.environment.dto.UpdateEnvironmentRequest;
import com.vincent.devops.environment.dto.UpdateEnvironmentStatusRequest;
import com.vincent.devops.environment.service.EnvironmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目环境管理接口。
 *
 * <p>基础路径：{@code /api/v1/projects/{projectId}/environments}。</p>
 * <p>所有接口都会校验环境是否属于指定项目。环境编码在同一个项目内唯一。</p>
 * <p>环境编码创建后不可修改；删除接口执行逻辑删除，启用中的环境必须先停用。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    /**
     * 查询项目下的环境分页列表。
     *
     * <p>支持按环境编码、名称、Namespace 模糊搜索，并按环境状态筛选。</p>
     *
     * @param projectId 项目 ID
     * @param keyword 环境编码、名称或 Namespace 关键字，可为空
     * @param status 环境状态，可选值为 {@code ACTIVE}、{@code DISABLED}
     * @param current 当前页，从 1 开始，小于 1 时按 1 处理
     * @param size 每页数量，范围限制为 1~100
     * @return 环境分页结果
     */
    @GetMapping
    @PreAuthorize("@projectAccessService.canRead(authentication, #projectId)")
    public ApiResponse<PageResponse<EnvironmentResponse>> page(
            @PathVariable Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size
    ) {
        Page<EnvironmentEntity> page = environmentService.page(projectId, keyword, status, current, size);
        return ApiResponse.ok(PageResponse.from(page, EnvironmentResponse::from));
    }

    /**
     * 查询环境详情。
     *
     * @param projectId 项目 ID
     * @param id 环境 ID
     * @return 环境详情
     * @throws com.vincent.devops.common.ResourceNotFoundException 项目或环境不存在时抛出，接口返回 404
     */
    @GetMapping("/{id}")
    @PreAuthorize("@projectAccessService.canRead(authentication, #projectId)")
    public ApiResponse<EnvironmentResponse> get(
            @PathVariable Long projectId,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(EnvironmentResponse.from(environmentService.get(projectId, id)));
    }

    /**
     * 创建项目环境。
     *
     * <p>只能为启用中的项目创建环境，环境创建后状态默认为 {@code ACTIVE}。</p>
     *
     * @param projectId 项目 ID
     * @param request 环境编码、名称、Namespace、集群和描述
     * @return 创建后的环境详情
     * @throws org.springframework.dao.DuplicateKeyException 当前项目下环境编码重复时抛出，接口返回 409
     */
    @PostMapping
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<EnvironmentResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateEnvironmentRequest request
    ) {
        return ApiResponse.ok(EnvironmentResponse.from(environmentService.create(projectId, request)));
    }

    /**
     * 修改环境基本信息。
     *
     * <p>环境编码是稳定标识，本接口只允许修改名称、Namespace、集群和描述。</p>
     *
     * @param projectId 项目 ID
     * @param id 环境 ID
     * @param request 新的环境基本信息
     * @return 修改后的环境详情
     */
    @PutMapping("/{id}")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<EnvironmentResponse> update(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateEnvironmentRequest request
    ) {
        return ApiResponse.ok(EnvironmentResponse.from(environmentService.update(projectId, id, request)));
    }

    /**
     * 修改环境状态。
     *
     * <p>支持 {@code ACTIVE} 和 {@code DISABLED}。重复提交当前状态不会重复更新数据库。</p>
     *
     * @param projectId 项目 ID
     * @param id 环境 ID
     * @param request 目标状态
     * @return 修改后的环境详情
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<EnvironmentResponse> updateStatus(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateEnvironmentStatusRequest request
    ) {
        return ApiResponse.ok(EnvironmentResponse.from(environmentService.updateStatus(projectId, id, request)));
    }

    /**
     * 逻辑删除环境。
     *
     * <p>启用中的环境必须先停用，删除后环境不再出现在默认列表中，数据仍保留。</p>
     *
     * @param projectId 项目 ID
     * @param id 环境 ID
     * @return 空数据成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@projectAccessService.canDelete(authentication, #projectId)")
    public ApiResponse<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long id
    ) {
        environmentService.delete(projectId, id);
        return ApiResponse.ok(null);
    }
}
