package com.vincent.devops.microservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.common.ApiResponse;
import com.vincent.devops.common.PageResponse;
import com.vincent.devops.microservice.domain.MicroserviceEntity;
import com.vincent.devops.microservice.dto.CreateMicroserviceRequest;
import com.vincent.devops.microservice.dto.BindMicroserviceEnvironmentsRequest;
import com.vincent.devops.microservice.dto.MicroserviceResponse;
import com.vincent.devops.microservice.dto.UpdateMicroserviceRequest;
import com.vincent.devops.microservice.dto.UpdateMicroserviceStatusRequest;
import com.vincent.devops.microservice.service.MicroserviceService;
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
 * 项目微服务管理接口。
 *
 * <p>基础路径：{@code /api/v1/projects/{projectId}/services}。</p>
 * <p>微服务编码在项目内唯一，环境关联使用独立接口维护。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/services")
public class MicroserviceController {

    private final MicroserviceService microserviceService;

    public MicroserviceController(MicroserviceService microserviceService) {
        this.microserviceService = microserviceService;
    }

    /**
     * 查询项目下的微服务分页列表。
     *
     * @param projectId 项目 ID
     * @param keyword 微服务编码、名称或仓库地址关键字，可为空
     * @param status 微服务状态，可选值为 ACTIVE、DISABLED
     * @param current 当前页，从 1 开始
     * @param size 每页数量，范围限制为 1~100
     * @return 微服务分页结果
     */
    @GetMapping
    @PreAuthorize("@projectAccessService.canRead(authentication, #projectId)")
    public ApiResponse<PageResponse<MicroserviceResponse>> page(
            @PathVariable Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size
    ) {
        Page<MicroserviceEntity> page = microserviceService.page(projectId, keyword, status, current, size);
        return ApiResponse.ok(PageResponse.from(page, MicroserviceResponse::from));
    }

    /**
     * 查询微服务详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@projectAccessService.canRead(authentication, #projectId)")
    public ApiResponse<MicroserviceResponse> get(
            @PathVariable Long projectId,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(MicroserviceResponse.from(microserviceService.get(projectId, id)));
    }

    /**
     * 创建微服务。
     *
     * <p>只有启用中的项目可以创建微服务，创建后状态默认为 ACTIVE。请求携带关联环境时，
     * 服务创建和全部环境关联在同一事务中完成。</p>
     */
    @PostMapping
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<MicroserviceResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateMicroserviceRequest request
    ) {
        return ApiResponse.ok(MicroserviceResponse.from(microserviceService.create(projectId, request)));
    }

    /**
     * 修改微服务基本信息，微服务编码不可修改。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<MicroserviceResponse> update(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateMicroserviceRequest request
    ) {
        return ApiResponse.ok(MicroserviceResponse.from(microserviceService.update(projectId, id, request)));
    }

    /**
     * 修改微服务状态，重复提交当前状态保持幂等。
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<MicroserviceResponse> updateStatus(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateMicroserviceStatusRequest request
    ) {
        return ApiResponse.ok(MicroserviceResponse.from(
                microserviceService.updateStatus(projectId, id, request)
        ));
    }

    /**
     * 逻辑删除微服务。
     *
     * <p>启用中的微服务不能删除；删除已停用的微服务时，会同步逻辑删除其全部环境关联，
     * 以避免后续列表出现孤立关联数据。</p>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@projectAccessService.canDelete(authentication, #projectId)")
    public ApiResponse<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long id
    ) {
        microserviceService.delete(projectId, id);
        return ApiResponse.ok(null);
    }

    /**
     * 将微服务关联到环境，重复关联保持幂等。
     *
     * @param projectId 项目 ID
     * @param serviceId 微服务 ID
     * @param environmentId 环境 ID
     */
    @PostMapping("/{serviceId}/environments/{environmentId}")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<Void> bindEnvironment(
            @PathVariable Long projectId,
            @PathVariable Long serviceId,
            @PathVariable Long environmentId
    ) {
        microserviceService.bindEnvironment(projectId, serviceId, environmentId);
        return ApiResponse.ok(null);
    }

    /**
     * 将一个微服务原子关联到同项目下的多个环境。
     *
     * <p>环境必须全部存在且启用；重复关联与并发重试不会生成重复记录。
     * 任一环境校验失败时，本次请求不产生任何新关联。</p>
     *
     * @param projectId 项目 ID
     * @param serviceId 微服务 ID
     * @param request 环境 ID 集合，单次最多 100 个且不能重复
     * @return 空数据成功响应
     */
    @PostMapping("/{serviceId}/environments/batch")
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<Void> bindEnvironments(
            @PathVariable Long projectId,
            @PathVariable Long serviceId,
            @Valid @RequestBody BindMicroserviceEnvironmentsRequest request
    ) {
        microserviceService.bindEnvironments(projectId, serviceId, request.uniqueEnvironmentIds());
        return ApiResponse.ok(null);
    }

    /**
     * 解除微服务与环境的关联。
     */
    @DeleteMapping("/{serviceId}/environments/{environmentId}")
    @PreAuthorize("@projectAccessService.canDelete(authentication, #projectId)")
    public ApiResponse<Void> unbindEnvironment(
            @PathVariable Long projectId,
            @PathVariable Long serviceId,
            @PathVariable Long environmentId
    ) {
        microserviceService.unbindEnvironment(projectId, serviceId, environmentId);
        return ApiResponse.ok(null);
    }
}
