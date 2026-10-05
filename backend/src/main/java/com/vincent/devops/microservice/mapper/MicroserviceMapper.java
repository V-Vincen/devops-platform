package com.vincent.devops.microservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.microservice.domain.MicroserviceEntity;
import com.vincent.devops.microservice.dto.MicroserviceEnvironmentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 微服务数据访问接口。
 */
@Mapper
public interface MicroserviceMapper extends BaseMapper<MicroserviceEntity> {

    /**
     * 以微服务为主表分页查询项目、环境关联信息。
     * 使用左连接保留尚未关联环境的历史微服务，避免全局列表出现数据遗漏。
     */
    @Select("""
            <script>
            SELECT se.id AS binding_id,
                   p.id AS project_id, p.code AS project_code, p.name AS project_name,
                   e.id AS environment_id, e.code AS environment_code, e.name AS environment_name,
                   s.id, s.service_code, s.service_name, s.repository_url, s.branch_name,
                   s.build_type, s.image_repository, s.port, s.replicas, s.cpu_limit,
                   s.memory_limit, s.status, s.description, s.created_at, s.updated_at
              FROM service s
              JOIN project p ON p.id = s.project_id AND p.deleted = FALSE
              LEFT JOIN service_environment se
                     ON se.service_id = s.id AND se.project_id = s.project_id AND se.deleted = FALSE
              LEFT JOIN environment e
                     ON e.id = se.environment_id AND e.project_id = s.project_id AND e.deleted = FALSE
             WHERE s.deleted = FALSE
            <if test="projectIds != null">
              <choose>
                <when test="projectIds.size() > 0">
                  AND s.project_id IN
                  <foreach collection="projectIds" item="projectId" open="(" separator="," close=")">
                    #{projectId}
                  </foreach>
                </when>
                <otherwise>AND 1 = 0</otherwise>
              </choose>
            </if>
            <if test="projectId != null">
              AND s.project_id = #{projectId}
            </if>
            <if test="environmentId != null">
              AND se.environment_id = #{environmentId}
            </if>
            <if test="keyword != null and keyword != ''">
              AND (s.service_code LIKE CONCAT('%', #{keyword}, '%')
                   OR s.service_name LIKE CONCAT('%', #{keyword}, '%')
                   OR s.repository_url LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null and status != ''">
              AND s.status = #{status}
            </if>
             ORDER BY p.created_at DESC, e.created_at DESC NULLS LAST, s.created_at DESC
            </script>
            """)
    Page<MicroserviceEnvironmentRow> selectEnvironmentPage(
            Page<MicroserviceEnvironmentRow> page,
            @Param("projectIds") List<Long> projectIds,
            @Param("projectId") Long projectId,
            @Param("environmentId") Long environmentId,
            @Param("keyword") String keyword,
            @Param("status") String status
    );
}
