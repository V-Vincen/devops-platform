package com.vincent.devops.environment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.dto.CreateEnvironmentRequest;
import com.vincent.devops.environment.dto.EnvironmentProjectRow;
import com.vincent.devops.environment.dto.UpdateEnvironmentRequest;
import com.vincent.devops.environment.dto.UpdateEnvironmentStatusRequest;

import java.util.List;

public interface EnvironmentService {

    /**
     * 查询项目下的环境分页列表。
     *
     * @param projectId 项目 ID
     * @param keyword 环境编码、名称或 Namespace 关键字
     * @param status 环境状态
     * @param current 当前页
     * @param size 每页数量
     * @return 环境分页结果
     */
    Page<EnvironmentEntity> page(Long projectId, String keyword, String status, long current, long size);

    /**
     * 查询当前用户可访问项目中的全局环境分页列表。
     *
     * @param projectIds 非管理员可访问的项目集合；管理员传 {@code null} 查询全部项目
     * @param projectId 可选项目筛选条件；会与 {@code projectIds} 共同生效
     */
    Page<EnvironmentProjectRow> pageAll(
            List<Long> projectIds,
            Long projectId,
            String keyword,
            String status,
            long current,
            long size
    );

    /**
     * 查询项目下的环境详情。
     *
     * @param projectId 项目 ID
     * @param id 环境 ID
     * @return 环境实体
     */
    EnvironmentEntity get(Long projectId, Long id);

    /**
     * 创建项目环境。
     *
     * @param projectId 项目 ID
     * @param request 创建参数
     * @return 创建后的环境实体
     */
    EnvironmentEntity create(Long projectId, CreateEnvironmentRequest request);

    /**
     * 修改环境基本信息。
     *
     * @param projectId 项目 ID
     * @param id 环境 ID
     * @param request 修改参数
     * @return 修改后的环境实体
     */
    EnvironmentEntity update(Long projectId, Long id, UpdateEnvironmentRequest request);

    /**
     * 修改环境状态。
     *
     * @param projectId 项目 ID
     * @param id 环境 ID
     * @param request 状态修改参数
     * @return 修改后的环境实体
     */
    EnvironmentEntity updateStatus(Long projectId, Long id, UpdateEnvironmentStatusRequest request);

    /**
     * 逻辑删除环境。
     *
     * @param projectId 项目 ID
     * @param id 环境 ID
     */
    void delete(Long projectId, Long id);

    /**
     * 原子逻辑删除多个环境。
     * 所有环境均已停用且未关联微服务时才会删除，任一校验失败将整体回滚。
     *
     * @param ids 待删除环境主键集合
     */
    void deleteBatch(List<Long> ids);
}
