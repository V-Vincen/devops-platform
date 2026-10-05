package com.vincent.devops.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.project.domain.ProjectEntity;
import com.vincent.devops.project.dto.CreateProjectRequest;
import com.vincent.devops.project.dto.UpdateProjectRequest;
import com.vincent.devops.project.dto.UpdateProjectStatusRequest;

import java.util.List;

public interface ProjectService {

    /**
     * 查询项目分页列表。
     *
     * @param keyword 项目编码或名称关键字
     * @param status 项目状态
     * @param current 当前页
     * @param size 每页数量
     * @return 项目分页结果
     */
    Page<ProjectEntity> page(String keyword, String status, long current, long size);

    /**
     * 按授权项目 ID 查询项目分页列表。
     */
    Page<ProjectEntity> pageByProjectIds(
            List<Long> projectIds,
            String keyword,
            String status,
            long current,
            long size
    );

    /**
     * 查询项目详情。
     *
     * @param id 项目 ID
     * @return 项目实体
     */
    ProjectEntity get(Long id);

    /**
     * 创建项目。
     *
     * @param request 创建参数
     * @return 创建后的项目实体
     */
    ProjectEntity create(CreateProjectRequest request);

    /**
     * 修改项目名称和描述。
     *
     * @param id 项目 ID
     * @param request 修改参数
     * @return 修改后的项目实体
     */
    ProjectEntity update(Long id, UpdateProjectRequest request);

    /**
     * 修改项目状态。
     *
     * @param id 项目 ID
     * @param request 状态修改参数
     * @return 修改后的项目实体
     */
    ProjectEntity updateStatus(Long id, UpdateProjectStatusRequest request);

    /**
     * 逻辑删除项目。
     *
     * @param id 项目 ID
     */
    void delete(Long id);

    /**
     * 原子逻辑删除多个项目。
     * 所有项目均已停用且不存在下级环境、微服务时才会删除；任一校验失败将整体回滚。
     *
     * @param ids 待删除项目主键集合
     */
    void deleteBatch(List<Long> ids);
}
