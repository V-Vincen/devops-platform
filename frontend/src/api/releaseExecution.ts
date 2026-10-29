import { http, type ApiResponse } from './http'

/** 构建执行记录；ID 始终使用字符串，避免浏览器处理长整型时丢失精度。 */
export interface PipelineRun {
  id: string
  projectId: string
  serviceId: string
  environmentId: string
  repositoryId: string
  buildSpecId: string
  triggerType: string
  externalEventId: string | null
  gitRef: string
  commitSha: string | null
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED'
  jenkinsJobName: string | null
  jenkinsBuildNumber: number | null
  jenkinsBuildUrl: string | null
  parameterSnapshot: string
  queuedAt: string | null
  startedAt: string | null
  finishedAt: string | null
  failureSummary: string | null
  createdAt: string
  updatedAt: string
}

/** 已产出的镜像制品；digest 是部署和回滚的不可变标识。 */
export interface ImageArtifact {
  id: string
  projectId: string
  serviceId: string
  pipelineRunId: string
  imageRepository: string
  imageTag: string
  imageDigest: string
  status: 'PENDING' | 'PUBLISHED' | 'FAILED'
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

/** 部署或回滚记录；环境由后端从构建记录继承。 */
export interface DeploymentRecord {
  id: string
  projectId: string
  serviceId: string
  environmentId: string
  pipelineRunId: string
  imageArtifactId: string
  previousDeploymentId: string | null
  operationType: 'DEPLOY' | 'ROLLBACK'
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED'
  helmReleaseName: string
  helmRevision: number | null
  deployedAt: string | null
  finishedAt: string | null
  failureSummary: string | null
  createdAt: string
  updatedAt: string
}

const executionPath = (projectId: string, serviceId: string) =>
  `/api/v1/projects/${projectId}/services/${serviceId}/release-executions`

/** 查询服务的构建记录，可按状态缩小范围。 */
export async function fetchPipelineRuns(projectId: string, serviceId: string, status?: PipelineRun['status']) {
  const response = await http.get<ApiResponse<PipelineRun[]>>(`${executionPath(projectId, serviceId)}/pipeline-runs`, {
    params: status ? { status } : undefined,
  })
  return response.data.data!
}

/** 查询服务已登记的镜像制品。 */
export async function fetchImageArtifacts(projectId: string, serviceId: string) {
  const response = await http.get<ApiResponse<ImageArtifact[]>>(`${executionPath(projectId, serviceId)}/image-artifacts`)
  return response.data.data!
}

/** 查询服务的部署与回滚记录。 */
export async function fetchDeploymentRecords(projectId: string, serviceId: string) {
  const response = await http.get<ApiResponse<DeploymentRecord[]>>(`${executionPath(projectId, serviceId)}/deployments`)
  return response.data.data!
}
