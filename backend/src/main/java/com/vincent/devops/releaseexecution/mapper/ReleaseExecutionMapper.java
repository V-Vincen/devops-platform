package com.vincent.devops.releaseexecution.mapper;

import com.vincent.devops.releaseexecution.dto.DeploymentRow;
import com.vincent.devops.releaseexecution.dto.ImageArtifactRow;
import com.vincent.devops.releaseexecution.dto.PipelineRunRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 构建、镜像和部署记录访问层；所有查询都带项目和服务归属条件。 */
@Mapper
public interface ReleaseExecutionMapper {

    @Select("""
            SELECT CAST(id AS varchar) AS id, CAST(project_id AS varchar) AS projectId,
                   CAST(service_id AS varchar) AS serviceId, CAST(environment_id AS varchar) AS environmentId,
                   CAST(repository_id AS varchar) AS repositoryId, CAST(build_spec_id AS varchar) AS buildSpecId,
                   trigger_type AS triggerType, external_event_id AS externalEventId, git_ref AS gitRef,
                   commit_sha AS commitSha, status, jenkins_job_name AS jenkinsJobName,
                   jenkins_build_number AS jenkinsBuildNumber, jenkins_build_url AS jenkinsBuildUrl,
                   CAST(parameter_snapshot AS varchar) AS parameterSnapshot, queued_at AS queuedAt,
                   started_at AS startedAt, finished_at AS finishedAt, failure_summary AS failureSummary,
                   created_at AS createdAt, updated_at AS updatedAt
              FROM pipeline_run
             WHERE project_id = #{projectId} AND service_id = #{serviceId} AND id = #{id}
            """)
    PipelineRunRow selectPipelineRun(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                                     @Param("id") Long id);

    @Select("""
            SELECT CAST(id AS varchar) AS id, CAST(project_id AS varchar) AS projectId,
                   CAST(service_id AS varchar) AS serviceId, CAST(environment_id AS varchar) AS environmentId,
                   CAST(repository_id AS varchar) AS repositoryId, CAST(build_spec_id AS varchar) AS buildSpecId,
                   trigger_type AS triggerType, external_event_id AS externalEventId, git_ref AS gitRef,
                   commit_sha AS commitSha, status, jenkins_job_name AS jenkinsJobName,
                   jenkins_build_number AS jenkinsBuildNumber, jenkins_build_url AS jenkinsBuildUrl,
                   CAST(parameter_snapshot AS varchar) AS parameterSnapshot, queued_at AS queuedAt,
                   started_at AS startedAt, finished_at AS finishedAt, failure_summary AS failureSummary,
                   created_at AS createdAt, updated_at AS updatedAt
              FROM pipeline_run
             WHERE repository_id = #{repositoryId} AND external_event_id = #{externalEventId}
            """)
    PipelineRunRow selectPipelineRunByExternalEvent(@Param("repositoryId") Long repositoryId,
                                                    @Param("externalEventId") String externalEventId);

    @Select("""
            <script>
            SELECT CAST(id AS varchar) AS id, CAST(project_id AS varchar) AS projectId,
                   CAST(service_id AS varchar) AS serviceId, CAST(environment_id AS varchar) AS environmentId,
                   CAST(repository_id AS varchar) AS repositoryId, CAST(build_spec_id AS varchar) AS buildSpecId,
                   trigger_type AS triggerType, external_event_id AS externalEventId, git_ref AS gitRef,
                   commit_sha AS commitSha, status, jenkins_job_name AS jenkinsJobName,
                   jenkins_build_number AS jenkinsBuildNumber, jenkins_build_url AS jenkinsBuildUrl,
                   CAST(parameter_snapshot AS varchar) AS parameterSnapshot, queued_at AS queuedAt,
                   started_at AS startedAt, finished_at AS finishedAt, failure_summary AS failureSummary,
                   created_at AS createdAt, updated_at AS updatedAt
              FROM pipeline_run
             WHERE project_id = #{projectId} AND service_id = #{serviceId}
               <if test="status != null and status != ''">AND status = #{status}</if>
             ORDER BY created_at DESC, id DESC
             LIMIT 100
            </script>
            """)
    List<PipelineRunRow> selectPipelineRuns(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                                            @Param("status") String status);

    @Insert("""
            INSERT INTO pipeline_run
                   (id, project_id, service_id, environment_id, repository_id, build_spec_id,
                    trigger_type, external_event_id, git_ref, commit_sha, status, parameter_snapshot,
                    queued_at, created_at, updated_at)
            VALUES (#{id}, #{projectId}, #{serviceId}, #{environmentId}, #{repositoryId}, #{buildSpecId},
                    #{triggerType}, #{externalEventId}, #{gitRef}, #{commitSha}, 'PENDING',
                    CAST(#{parameterSnapshot} AS jsonb), #{queuedAt}, #{queuedAt}, #{queuedAt})
            """)
    int insertPipelineRun(@Param("id") Long id, @Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                          @Param("environmentId") Long environmentId, @Param("repositoryId") Long repositoryId,
                          @Param("buildSpecId") Long buildSpecId, @Param("triggerType") String triggerType,
                          @Param("externalEventId") String externalEventId, @Param("gitRef") String gitRef,
                          @Param("commitSha") String commitSha, @Param("parameterSnapshot") String parameterSnapshot,
                          @Param("queuedAt") LocalDateTime queuedAt);

    @Update("""
            UPDATE pipeline_run
               SET status = #{status}, jenkins_job_name = COALESCE(#{jenkinsJobName}, jenkins_job_name),
                   jenkins_build_number = COALESCE(#{jenkinsBuildNumber}, jenkins_build_number),
                   jenkins_build_url = COALESCE(#{jenkinsBuildUrl}, jenkins_build_url),
                   started_at = COALESCE(#{startedAt}, started_at),
                   finished_at = COALESCE(#{finishedAt}, finished_at), failure_summary = #{failureSummary},
                   updated_at = #{updatedAt}
             WHERE project_id = #{projectId} AND service_id = #{serviceId} AND id = #{id}
               AND status = #{currentStatus}
            """)
    int updatePipelineRunStatus(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                                @Param("id") Long id, @Param("status") String status,
                                @Param("currentStatus") String currentStatus,
                                @Param("jenkinsJobName") String jenkinsJobName,
                                @Param("jenkinsBuildNumber") Long jenkinsBuildNumber,
                                @Param("jenkinsBuildUrl") String jenkinsBuildUrl,
                                @Param("startedAt") LocalDateTime startedAt,
                                @Param("finishedAt") LocalDateTime finishedAt,
                                @Param("failureSummary") String failureSummary,
                                @Param("updatedAt") LocalDateTime updatedAt);

    @Select("""
            SELECT COUNT(*) > 0
              FROM service_environment_policy
             WHERE project_id = #{projectId} AND service_id = #{serviceId}
               AND environment_id = #{environmentId} AND deleted = FALSE AND status = 'ACTIVE'
            """)
    boolean existsActivePolicy(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                               @Param("environmentId") Long environmentId);

    @Select("""
            SELECT CAST(id AS varchar) AS id, CAST(project_id AS varchar) AS projectId,
                   CAST(service_id AS varchar) AS serviceId, CAST(pipeline_run_id AS varchar) AS pipelineRunId,
                   image_repository AS imageRepository, image_tag AS imageTag, image_digest AS imageDigest,
                   status, published_at AS publishedAt, created_at AS createdAt, updated_at AS updatedAt
              FROM image_artifact
             WHERE project_id = #{projectId} AND service_id = #{serviceId} AND id = #{id}
            """)
    ImageArtifactRow selectImageArtifact(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                                         @Param("id") Long id);

    @Select("""
            SELECT CAST(id AS varchar) AS id, CAST(project_id AS varchar) AS projectId,
                   CAST(service_id AS varchar) AS serviceId, CAST(pipeline_run_id AS varchar) AS pipelineRunId,
                   image_repository AS imageRepository, image_tag AS imageTag, image_digest AS imageDigest,
                   status, published_at AS publishedAt, created_at AS createdAt, updated_at AS updatedAt
              FROM image_artifact
             WHERE image_repository = #{imageRepository} AND image_digest = #{imageDigest}
            """)
    ImageArtifactRow selectImageArtifactByDigest(@Param("imageRepository") String imageRepository,
                                                 @Param("imageDigest") String imageDigest);

    @Select("""
            SELECT CAST(id AS varchar) AS id, CAST(project_id AS varchar) AS projectId,
                   CAST(service_id AS varchar) AS serviceId, CAST(pipeline_run_id AS varchar) AS pipelineRunId,
                   image_repository AS imageRepository, image_tag AS imageTag, image_digest AS imageDigest,
                   status, published_at AS publishedAt, created_at AS createdAt, updated_at AS updatedAt
              FROM image_artifact
             WHERE project_id = #{projectId} AND service_id = #{serviceId}
             ORDER BY created_at DESC, id DESC
             LIMIT 100
            """)
    List<ImageArtifactRow> selectImageArtifacts(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId);

    @Insert("""
            INSERT INTO image_artifact
                   (id, project_id, service_id, pipeline_run_id, image_repository, image_tag, image_digest,
                    status, created_at, updated_at)
            VALUES (#{id}, #{projectId}, #{serviceId}, #{pipelineRunId}, #{imageRepository}, #{imageTag},
                    #{imageDigest}, 'PENDING', #{createdAt}, #{createdAt})
            """)
    int insertImageArtifact(@Param("id") Long id, @Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                            @Param("pipelineRunId") Long pipelineRunId, @Param("imageRepository") String imageRepository,
                            @Param("imageTag") String imageTag, @Param("imageDigest") String imageDigest,
                            @Param("createdAt") LocalDateTime createdAt);

    @Update("""
            UPDATE image_artifact
               SET status = #{status}, published_at = CASE WHEN #{status} = 'PUBLISHED' THEN COALESCE(published_at, #{updatedAt}) ELSE published_at END,
                   updated_at = #{updatedAt}
             WHERE project_id = #{projectId} AND service_id = #{serviceId} AND id = #{id}
               AND status = #{currentStatus}
            """)
    int updateImageArtifactStatus(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                                  @Param("id") Long id, @Param("status") String status,
                                  @Param("currentStatus") String currentStatus,
                                  @Param("updatedAt") LocalDateTime updatedAt);

    @Select("""
            SELECT CAST(id AS varchar) AS id, CAST(project_id AS varchar) AS projectId,
                   CAST(service_id AS varchar) AS serviceId, CAST(environment_id AS varchar) AS environmentId,
                   CAST(pipeline_run_id AS varchar) AS pipelineRunId, CAST(image_artifact_id AS varchar) AS imageArtifactId,
                   CAST(previous_deployment_id AS varchar) AS previousDeploymentId, operation_type AS operationType,
                   status, helm_release_name AS helmReleaseName, helm_revision AS helmRevision,
                   deployed_at AS deployedAt, finished_at AS finishedAt, failure_summary AS failureSummary,
                   created_at AS createdAt, updated_at AS updatedAt
              FROM deployment_record
             WHERE project_id = #{projectId} AND service_id = #{serviceId} AND id = #{id}
            """)
    DeploymentRow selectDeployment(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                                   @Param("id") Long id);

    @Select("""
            SELECT CAST(id AS varchar) AS id, CAST(project_id AS varchar) AS projectId,
                   CAST(service_id AS varchar) AS serviceId, CAST(environment_id AS varchar) AS environmentId,
                   CAST(pipeline_run_id AS varchar) AS pipelineRunId, CAST(image_artifact_id AS varchar) AS imageArtifactId,
                   CAST(previous_deployment_id AS varchar) AS previousDeploymentId, operation_type AS operationType,
                   status, helm_release_name AS helmReleaseName, helm_revision AS helmRevision,
                   deployed_at AS deployedAt, finished_at AS finishedAt, failure_summary AS failureSummary,
                   created_at AS createdAt, updated_at AS updatedAt
              FROM deployment_record
             WHERE project_id = #{projectId} AND service_id = #{serviceId}
             ORDER BY created_at DESC, id DESC
             LIMIT 100
            """)
    List<DeploymentRow> selectDeployments(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId);

    @Select("""
            <script>
            SELECT CAST(id AS varchar) AS id, CAST(project_id AS varchar) AS projectId,
                   CAST(service_id AS varchar) AS serviceId, CAST(environment_id AS varchar) AS environmentId,
                   CAST(pipeline_run_id AS varchar) AS pipelineRunId, CAST(image_artifact_id AS varchar) AS imageArtifactId,
                   CAST(previous_deployment_id AS varchar) AS previousDeploymentId, operation_type AS operationType,
                   status, helm_release_name AS helmReleaseName, helm_revision AS helmRevision,
                   deployed_at AS deployedAt, finished_at AS finishedAt, failure_summary AS failureSummary,
                   created_at AS createdAt, updated_at AS updatedAt
              FROM deployment_record
             WHERE project_id = #{projectId} AND service_id = #{serviceId}
               AND pipeline_run_id = #{pipelineRunId} AND image_artifact_id = #{imageArtifactId}
               AND operation_type = #{operationType}
               <choose>
                 <when test="previousDeploymentId == null">
                   AND previous_deployment_id IS NULL
                 </when>
                 <otherwise>
                   AND previous_deployment_id = #{previousDeploymentId}
                 </otherwise>
               </choose>
             ORDER BY created_at ASC, id ASC
             LIMIT 1
            </script>
            """)
    DeploymentRow selectDeploymentByRequest(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                                            @Param("pipelineRunId") Long pipelineRunId,
                                            @Param("imageArtifactId") Long imageArtifactId,
                                            @Param("operationType") String operationType,
                                            @Param("previousDeploymentId") Long previousDeploymentId);

    @Insert("""
            INSERT INTO deployment_record
                   (id, project_id, service_id, environment_id, pipeline_run_id, image_artifact_id,
                    previous_deployment_id, operation_type, status, helm_release_name, created_at, updated_at)
            VALUES (#{id}, #{projectId}, #{serviceId}, #{environmentId}, #{pipelineRunId}, #{imageArtifactId},
                    #{previousDeploymentId}, #{operationType}, 'PENDING', #{helmReleaseName}, #{createdAt}, #{createdAt})
            """)
    int insertDeployment(@Param("id") Long id, @Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                         @Param("environmentId") Long environmentId, @Param("pipelineRunId") Long pipelineRunId,
                         @Param("imageArtifactId") Long imageArtifactId, @Param("previousDeploymentId") Long previousDeploymentId,
                         @Param("operationType") String operationType, @Param("helmReleaseName") String helmReleaseName,
                         @Param("createdAt") LocalDateTime createdAt);

    @Update("""
            UPDATE deployment_record
               SET status = #{status}, helm_revision = COALESCE(#{helmRevision}, helm_revision),
                   deployed_at = COALESCE(#{deployedAt}, deployed_at), finished_at = COALESCE(#{finishedAt}, finished_at),
                   failure_summary = #{failureSummary}, updated_at = #{updatedAt}
             WHERE project_id = #{projectId} AND service_id = #{serviceId} AND id = #{id}
               AND status = #{currentStatus}
            """)
    int updateDeploymentStatus(@Param("projectId") Long projectId, @Param("serviceId") Long serviceId,
                               @Param("id") Long id, @Param("status") String status,
                               @Param("currentStatus") String currentStatus,
                               @Param("helmRevision") Integer helmRevision, @Param("deployedAt") LocalDateTime deployedAt,
                               @Param("finishedAt") LocalDateTime finishedAt, @Param("failureSummary") String failureSummary,
                               @Param("updatedAt") LocalDateTime updatedAt);
}
