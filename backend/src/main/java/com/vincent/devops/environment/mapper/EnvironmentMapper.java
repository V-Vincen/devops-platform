package com.vincent.devops.environment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.dto.EnvironmentProjectRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EnvironmentMapper extends BaseMapper<EnvironmentEntity> {

    /**
     * 按当前用户可访问项目范围分页查询环境，并一次返回所属项目的展示信息。
     * 查询在数据库完成筛选和分页，避免前端逐项目拉取全部环境造成请求放大。
     */
    @Select("""
            <script>
            SELECT e.id, e.project_id, p.code AS project_code, p.name AS project_name,
                   e.code, e.name, e.namespace, e.cluster_name, e.status, e.description,
                   e.created_at, e.updated_at
              FROM environment e
              JOIN project p ON p.id = e.project_id AND p.deleted = FALSE
             WHERE e.deleted = FALSE
            <if test="projectIds != null">
              <choose>
                <when test="projectIds.size() > 0">
                  AND e.project_id IN
                  <foreach collection="projectIds" item="accessibleProjectId" open="(" separator="," close=")">
                    #{accessibleProjectId}
                  </foreach>
                </when>
                <otherwise>AND 1 = 0</otherwise>
              </choose>
            </if>
            <if test="projectId != null">
              AND e.project_id = #{projectId}
            </if>
            <if test="keyword != null and keyword != ''">
              AND (e.code LIKE CONCAT('%', #{keyword}, '%')
                   OR e.name LIKE CONCAT('%', #{keyword}, '%')
                   OR e.namespace LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null and status != ''">
              AND e.status = #{status}
            </if>
             ORDER BY p.created_at DESC, e.created_at DESC
            </script>
            """)
    Page<EnvironmentProjectRow> selectProjectPage(
            Page<EnvironmentProjectRow> page,
            @Param("projectIds") List<Long> projectIds,
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("status") String status
    );
}
