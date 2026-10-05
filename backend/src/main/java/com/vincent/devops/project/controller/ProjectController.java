package com.vincent.devops.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.common.ApiResponse;
import com.vincent.devops.common.BatchDeleteRequest;
import com.vincent.devops.common.PageResponse;
import com.vincent.devops.auth.security.ProjectAccessService;
import com.vincent.devops.project.domain.ProjectEntity;
import com.vincent.devops.project.dto.CreateProjectRequest;
import com.vincent.devops.project.dto.ProjectResponse;
import com.vincent.devops.project.dto.UpdateProjectRequest;
import com.vincent.devops.project.dto.UpdateProjectStatusRequest;
import com.vincent.devops.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目管理接口。
 *
 * <p>基础路径：{@code /api/v1/projects}。</p>
 * <p>项目编码创建后不可修改；删除接口执行逻辑删除，启用中的项目必须先停用。</p>
 * <p>鉴权和项目归属校验将在 RBAC 模块接入后补齐，目前用于内部测试环境联调。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;

    public ProjectController(ProjectService projectService, ProjectAccessService projectAccessService) {
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
    }

    /**
     * 查询项目分页列表。
     *
     * <p>支持按项目编码、项目名称模糊搜索，并按项目状态筛选。</p>
     *
     * @param keyword 项目编码或名称关键字，可为空
     * @param status 项目状态，可选值为 {@code ACTIVE}、{@code DISABLED}
     * @param current 当前页，从 1 开始，小于 1 时按 1 处理
     * @param size 每页数量，范围限制为 1~100
     * @return 项目分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<ProjectResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            Authentication authentication
    ) {
        Page<ProjectEntity> page;
        if (!projectAccessService.securityEnabled() || projectAccessService.isAdmin(authentication)) {
            page = projectService.page(keyword, status, current, size);
        } else {
            page = projectService.pageByProjectIds(
                    projectAccessService.accessibleProjectIds(authentication),
                    keyword,
                    status,
                    current,
                    size
            );
        }
        return ApiResponse.ok(PageResponse.from(page, ProjectResponse::from));
    }

    /**
     * 查询项目详情。
     *
     * @param id 项目 ID
     * @return 项目详情
     * @throws com.vincent.devops.common.ResourceNotFoundException 项目不存在时抛出，接口返回 404
     */
    @GetMapping("/{id}")
    @PreAuthorize("@projectAccessService.canRead(authentication, #id)")
    public ApiResponse<ProjectResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(ProjectResponse.from(projectService.get(id)));
    }

    /**
     * 创建项目。
     *
     * <p>项目创建后状态默认为 {@code ACTIVE}，项目编码在平台内唯一。</p>
     *
     * @param request 项目编码、名称和描述
     * @return 创建后的项目详情
     * @throws org.springframework.dao.DuplicateKeyException 项目编码重复时抛出，接口返回 409
     */
    @PostMapping
    @PreAuthorize("@projectAccessService.canCreate(authentication)")
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.ok(ProjectResponse.from(projectService.create(request)));
    }

    /**
     * 修改项目基本信息。
     *
     * <p>项目编码是稳定标识，本接口只允许修改名称和描述。</p>
     *
     * @param id 项目 ID
     * @param request 新的项目名称和描述
     * @return 修改后的项目详情
     */
    @PutMapping("/{id}")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #id)")
    public ApiResponse<ProjectResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return ApiResponse.ok(ProjectResponse.from(projectService.update(id, request)));
    }

    /**
     * 修改项目状态。
     *
     * <p>支持 {@code ACTIVE} 和 {@code DISABLED}。重复提交当前状态不会重复更新数据库。</p>
     *
     * @param id 项目 ID
     * @param request 目标状态
     * @return 修改后的项目详情
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #id)")
    public ApiResponse<ProjectResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectStatusRequest request
    ) {
        return ApiResponse.ok(ProjectResponse.from(projectService.updateStatus(id, request)));
    }

    /**
     * 逻辑删除项目。
     *
     * <p>为了避免误删，启用中的项目必须先停用。删除后项目不再出现在默认列表中，数据仍保留。</p>
     *
     * @param id 项目 ID
     * @return 空数据成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@projectAccessService.canDelete(authentication, #id)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.ok(null);
    }

    /**
     * 原子逻辑删除多个项目。
     *
     * <p>只有管理员可调用。服务层会先校验全部项目均已停用且不存在下级资源，
     * 任一项目不满足条件时整批不产生删除结果。</p>
     *
     * @param request 待删除项目编号集合，单次最多 100 条
     * @return 空数据成功响应
     */
    @DeleteMapping("/batch")
    @PreAuthorize("@projectAccessService.canDeleteAll(authentication)")
    public ApiResponse<Void> deleteBatch(@Valid @RequestBody BatchDeleteRequest request) {
        projectService.deleteBatch(request.uniqueIds());
        return ApiResponse.ok(null);
    }
}
