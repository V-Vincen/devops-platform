package com.vincent.devops.releaseconfig.mapper;

import com.vincent.devops.releaseconfig.dto.EnvironmentPolicyResponse;
import com.vincent.devops.releaseconfig.dto.ReleaseConfigurationRow;
import com.vincent.devops.releaseconfig.dto.WebhookReleaseTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 发布配置聚合查询与更新；所有调用方必须先完成项目归属校验。 */
@Mapper
public interface ReleaseConfigurationMapper {

    /**
     * 为新服务创建默认发布配置。默认值只用于模拟数据，凭据引用始终为空，发布策略由环境关联后再创建。
     */
    @Insert("""
            INSERT INTO code_repository
                   (id, project_id, repository_key, provider, clone_url, default_branch, status, created_at, updated_at, deleted)
            VALUES (#{repositoryId}, #{projectId}, #{repositoryKey}, 'GITHUB', #{cloneUrl}, #{defaultBranch},
                    'ACTIVE', #{createdAt}, #{createdAt}, FALSE)
            """)
    int insertDefaultRepository(@Param("repositoryId") Long repositoryId, @Param("projectId") Long projectId,
                                @Param("repositoryKey") String repositoryKey, @Param("cloneUrl") String cloneUrl,
                                @Param("defaultBranch") String defaultBranch, @Param("createdAt") java.time.LocalDateTime createdAt);

    @Insert("""
            INSERT INTO service_build_spec
                   (id, project_id, service_id, repository_id, source_root, change_path_rules, build_workdir,
                    build_type, dockerfile_path, docker_context_path, image_repository, status, created_at, updated_at, deleted)
            VALUES (#{buildSpecId}, #{projectId}, #{serviceId}, #{repositoryId}, '/', '["/**"]'::jsonb, '.',
                    #{buildType}, 'Dockerfile', '.', #{imageRepository}, 'ACTIVE', #{createdAt}, #{createdAt}, FALSE)
            """)
    int insertDefaultBuildSpec(@Param("buildSpecId") Long buildSpecId, @Param("projectId") Long projectId,
                               @Param("serviceId") Long serviceId, @Param("repositoryId") Long repositoryId,
                               @Param("buildType") String buildType, @Param("imageRepository") String imageRepository,
                               @Param("createdAt") java.time.LocalDateTime createdAt);

    /** 为所有当前有效的服务环境关联创建默认“仅手动发布”策略；重复绑定是幂等操作。 */
    @Insert("""
            <script>
            INSERT INTO service_environment_policy
                   (id, project_id, service_environment_id, service_id, environment_id, release_policy,
                    approval_required, status, created_at, updated_at, deleted)
            SELECT se.id, se.project_id, se.id, se.service_id, se.environment_id, 'MANUAL_ONLY', TRUE,
                   'ACTIVE', #{createdAt}, #{createdAt}, FALSE
              FROM service_environment se
             WHERE se.project_id = #{projectId} AND se.service_id = #{serviceId}
               AND se.environment_id IN
               <foreach collection="environmentIds" item="environmentId" open="(" separator="," close=")">
                 #{environmentId}
               </foreach>
               AND se.deleted = FALSE
            ON CONFLICT (service_environment_id) WHERE deleted = FALSE DO NOTHING
            </script>
            """)
    int insertDefaultPolicies(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                              @Param("environmentIds") List<Long> environmentIds,
                              @Param("createdAt") java.time.LocalDateTime createdAt);

    /** 解除服务环境关联时同步逻辑删除对应策略，避免后续再次关联出现重复策略。 */
    @Update("""
            UPDATE service_environment_policy SET deleted = TRUE, updated_at = #{updatedAt}
             WHERE project_id = #{projectId} AND service_id = #{serviceId} AND environment_id = #{environmentId}
               AND deleted = FALSE
            """)
    int logicallyDeletePolicy(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                              @Param("environmentId") Long environmentId,
                              @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Select("""
            SELECT CAST(s.id AS varchar) AS serviceId,
                   CAST(r.id AS varchar) AS repositoryId, r.repository_key AS repositoryKey, r.provider,
                   r.clone_url AS cloneUrl, r.default_branch AS defaultBranch,
                   r.credential_reference AS credentialReference,
                   CAST(b.id AS varchar) AS buildSpecId, b.source_root AS sourceRoot,
                   CAST(b.change_path_rules AS varchar) AS changePathRules, b.build_workdir AS buildWorkdir,
                   b.build_type AS buildType, b.build_command AS buildCommand,
                   b.dockerfile_path AS dockerfilePath, b.docker_context_path AS dockerContextPath,
                   b.image_repository AS imageRepository
              FROM service s
              JOIN service_build_spec b ON b.service_id = s.id AND b.deleted = FALSE
              JOIN code_repository r ON r.id = b.repository_id AND r.deleted = FALSE
             WHERE s.project_id = #{projectId} AND s.id = #{serviceId} AND s.deleted = FALSE
            """)
    ReleaseConfigurationRow selectConfiguration(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId);

    @Select("""
            SELECT CAST(p.environment_id AS varchar) AS environmentId, e.code AS environmentCode,
                   e.name AS environmentName, p.release_policy AS releasePolicy,
                   p.approval_required AS approvalRequired, p.status
              FROM service_environment_policy p
              JOIN environment e ON e.id = p.environment_id AND e.deleted = FALSE
             WHERE p.project_id = #{projectId} AND p.service_id = #{serviceId}
               AND p.deleted = FALSE
             ORDER BY e.code
            """)
    List<EnvironmentPolicyResponse> selectPolicies(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId);

    @Select("""
            SELECT CAST(r.id AS varchar) AS repositoryId, CAST(r.project_id AS varchar) AS projectId,
                   CAST(b.service_id AS varchar) AS serviceId, CAST(p.environment_id AS varchar) AS environmentId,
                   r.default_branch AS defaultBranch, b.source_root AS sourceRoot,
                   CAST(b.change_path_rules AS varchar) AS changePathRules, p.release_policy AS releasePolicy
              FROM code_repository r
              JOIN service_build_spec b ON b.repository_id = r.id AND b.deleted = FALSE AND b.status = 'ACTIVE'
              JOIN service_environment_policy p ON p.service_id = b.service_id AND p.project_id = b.project_id
                   AND p.deleted = FALSE AND p.status = 'ACTIVE'
             WHERE r.provider = #{provider} AND r.repository_key = #{repositoryKey}
               AND r.deleted = FALSE AND r.status = 'ACTIVE'
             ORDER BY b.service_id, p.environment_id
            """)
    List<WebhookReleaseTarget> selectWebhookTargets(@Param("provider") String provider,
                                                    @Param("repositoryKey") String repositoryKey);

    @Update("""
            UPDATE code_repository SET repository_key = #{repositoryKey}, provider = #{provider},
                clone_url = #{cloneUrl}, default_branch = #{defaultBranch},
                credential_reference = #{credentialReference}, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{repositoryId} AND project_id = #{projectId} AND deleted = FALSE
            """)
    int updateRepository(@Param("projectId") Long projectId, @Param("repositoryId") Long repositoryId,
                         @Param("repositoryKey") String repositoryKey, @Param("provider") String provider,
                         @Param("cloneUrl") String cloneUrl, @Param("defaultBranch") String defaultBranch,
                         @Param("credentialReference") String credentialReference);

    @Update("""
            UPDATE service_build_spec SET source_root = #{sourceRoot}, change_path_rules = CAST(#{changePathRules} AS jsonb),
                build_workdir = #{buildWorkdir}, build_type = #{buildType}, build_command = #{buildCommand},
                dockerfile_path = #{dockerfilePath}, docker_context_path = #{dockerContextPath},
                image_repository = #{imageRepository}, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{buildSpecId} AND project_id = #{projectId} AND service_id = #{serviceId} AND deleted = FALSE
            """)
    int updateBuildSpec(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId, @Param("buildSpecId") Long buildSpecId,
                        @Param("sourceRoot") String sourceRoot, @Param("changePathRules") String changePathRules,
                        @Param("buildWorkdir") String buildWorkdir, @Param("buildType") String buildType,
                        @Param("buildCommand") String buildCommand, @Param("dockerfilePath") String dockerfilePath,
                        @Param("dockerContextPath") String dockerContextPath, @Param("imageRepository") String imageRepository);

    @Update("""
            UPDATE service_environment_policy SET release_policy = #{releasePolicy}, approval_required = #{approvalRequired},
                updated_at = CURRENT_TIMESTAMP
             WHERE project_id = #{projectId} AND service_id = #{serviceId} AND environment_id = #{environmentId}
               AND deleted = FALSE
            """)
    int updatePolicy(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                     @Param("environmentId") Long environmentId, @Param("releasePolicy") String releasePolicy,
                     @Param("approvalRequired") boolean approvalRequired);
}
