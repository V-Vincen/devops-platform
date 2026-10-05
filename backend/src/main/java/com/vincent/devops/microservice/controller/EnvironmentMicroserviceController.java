package com.vincent.devops.microservice.controller;

import com.vincent.devops.common.ApiResponse;
import com.vincent.devops.microservice.dto.MicroserviceResponse;
import com.vincent.devops.microservice.service.MicroserviceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 环境下的微服务查询接口。
 *
 * <p>该入口用于展示“项目 → 环境 → 微服务”关联结果。</p>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/environments/{environmentId}/services")
public class EnvironmentMicroserviceController {

    private final MicroserviceService microserviceService;

    public EnvironmentMicroserviceController(MicroserviceService microserviceService) {
        this.microserviceService = microserviceService;
    }

    /**
     * 查询环境下已经关联的微服务。
     *
     * @param projectId 项目 ID
     * @param environmentId 环境 ID
     * @return 环境微服务列表
     */
    @GetMapping
    @PreAuthorize("@projectAccessService.canRead(authentication, #projectId)")
    public ApiResponse<List<MicroserviceResponse>> list(
            @PathVariable Long projectId,
            @PathVariable Long environmentId
    ) {
        return ApiResponse.ok(microserviceService.listByEnvironment(projectId, environmentId)
                .stream()
                .map(MicroserviceResponse::from)
                .toList());
    }
}
