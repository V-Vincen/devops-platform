package com.vincent.devops.releaseconfig.controller;

import com.vincent.devops.common.ApiResponse;
import com.vincent.devops.releaseconfig.dto.ReleaseConfigurationResponse;
import com.vincent.devops.releaseconfig.dto.UpdateReleaseConfigurationRequest;
import com.vincent.devops.releaseconfig.service.ReleaseConfigurationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 项目内微服务的仓库、构建和环境发布策略接口。 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/services/{serviceId}/release-configuration")
public class ReleaseConfigurationController {
    private final ReleaseConfigurationService service;

    public ReleaseConfigurationController(ReleaseConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@projectAccessService.canRead(authentication, #projectId)")
    public ApiResponse<ReleaseConfigurationResponse> get(@PathVariable Long projectId, @PathVariable Long serviceId) {
        return ApiResponse.ok(service.get(projectId, serviceId));
    }

    @PutMapping
    @PreAuthorize("@projectAccessService.canOperate(authentication, #projectId)")
    public ApiResponse<ReleaseConfigurationResponse> update(@PathVariable Long projectId, @PathVariable Long serviceId,
                                                            @Valid @RequestBody UpdateReleaseConfigurationRequest request) {
        return ApiResponse.ok(service.update(projectId, serviceId, request));
    }
}
