package com.vincent.devops.microservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.microservice.domain.MicroserviceEntity;
import com.vincent.devops.microservice.dto.CreateMicroserviceRequest;
import com.vincent.devops.microservice.dto.EnvironmentOptionResponse;
import com.vincent.devops.microservice.dto.MicroserviceEnvironmentRow;
import com.vincent.devops.microservice.dto.UpdateMicroserviceRequest;
import com.vincent.devops.microservice.dto.UpdateMicroserviceStatusRequest;

import java.util.List;

/**
 * 微服务管理业务接口。
 */
public interface MicroserviceService {

    /**
     * 查询项目下的微服务分页列表。
     */
    Page<MicroserviceEntity> page(Long projectId, String keyword, String status, long current, long size);

    /**
     * 查询当前用户可访问范围内的微服务与环境关联分页结果。
     *
     * @param accessibleProjectIds 非管理员可访问项目集合；管理员传 {@code null}
     */
    Page<MicroserviceEnvironmentRow> pageEnvironmentBindings(
            List<Long> accessibleProjectIds,
            Long projectId,
            Long environmentId,
            String keyword,
            String status,
            long current,
            long size
    );

    /**
     * 查询可在新增微服务时选择的启用环境及其所属项目。
     */
    List<EnvironmentOptionResponse> listAvailableEnvironments(List<Long> accessibleProjectIds);

    /**
     * 查询项目下的微服务详情。
     */
    MicroserviceEntity get(Long projectId, Long id);

    /**
     * 创建项目微服务。
     */
    MicroserviceEntity create(Long projectId, CreateMicroserviceRequest request);

    /**
     * 修改微服务基本信息。
     */
    MicroserviceEntity update(Long projectId, Long id, UpdateMicroserviceRequest request);

    /**
     * 修改微服务状态。
     */
    MicroserviceEntity updateStatus(Long projectId, Long id, UpdateMicroserviceStatusRequest request);

    /**
     * 逻辑删除微服务。
     */
    void delete(Long projectId, Long id);

    /**
     * 原子逻辑删除多个微服务，并同步删除这些服务的全部环境关联。
     * 所有微服务均已停用时才会删除，任一校验失败将整体回滚。
     *
     * @param ids 待删除微服务主键集合
     */
    void deleteBatch(List<Long> ids);

    /**
     * 查询指定环境下已经关联的微服务。
     */
    List<MicroserviceEntity> listByEnvironment(Long projectId, Long environmentId);

    /**
     * 将微服务关联到项目环境。
     */
    void bindEnvironment(Long projectId, Long serviceId, Long environmentId);

    /**
     * 原子关联同一项目下的多个环境；重复关系及并发重试不会产生重复数据。
     */
    void bindEnvironments(Long projectId, Long serviceId, List<Long> environmentIds);

    /**
     * 解除微服务与项目环境的关联。
     */
    void unbindEnvironment(Long projectId, Long serviceId, Long environmentId);
}
