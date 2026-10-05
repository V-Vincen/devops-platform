package com.vincent.devops.microservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vincent.devops.microservice.domain.ServiceEnvironmentEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 微服务环境关联数据访问接口。
 */
@Mapper
public interface ServiceEnvironmentMapper extends BaseMapper<ServiceEnvironmentEntity> {

    /**
     * PostgreSQL 部分唯一索引与 {@code ON CONFLICT} 配合，使重复关联或并发重试安全地成为空操作。
     */
    @Insert("""
            <script>
            INSERT INTO service_environment
                   (id, project_id, service_id, environment_id, created_at, updated_at, deleted)
            VALUES
            <foreach collection="bindings" item="binding" separator=",">
              (#{binding.id}, #{binding.projectId}, #{binding.serviceId}, #{binding.environmentId},
               #{binding.createdAt}, #{binding.updatedAt}, #{binding.deleted})
            </foreach>
            ON CONFLICT (service_id, environment_id) WHERE deleted = FALSE DO NOTHING
            </script>
            """)
    int insertIgnoreExisting(@Param("bindings") List<ServiceEnvironmentEntity> bindings);
}
