import { http, type ApiResponse } from './http'
import type { PageResponse } from './project'

export interface Microservice {
  id: string
  projectId: string
  serviceCode: string
  serviceName: string
  repositoryUrl: string
  branchName: string
  buildType: string
  imageRepository: string
  port: number
  replicas: number
  cpuLimit: string | null
  memoryLimit: string | null
  status: 'ACTIVE' | 'DISABLED'
  description: string | null
  createdAt: string
  updatedAt: string
}

export type MicroserviceStatus = Microservice['status']

export interface MicroserviceQuery {
  keyword?: string
  status?: MicroserviceStatus
  current?: number
  size?: number
}

export interface CreateMicroserviceRequest {
  serviceCode: string
  serviceName: string
  repositoryUrl: string
  branchName: string
  buildType: string
  imageRepository: string
  port: number
  replicas: number
  cpuLimit?: string
  memoryLimit?: string
  description?: string
  /** 仅允许同一项目下的环境；传入时与服务创建处于同一个后端事务。 */
  environmentIds?: string[]
}

export type UpdateMicroserviceRequest = Omit<CreateMicroserviceRequest, 'serviceCode' | 'environmentIds'>

/** 微服务与环境关联后的全局分页行。环境字段为空时表示服务尚未关联环境。 */
export interface MicroserviceEnvironmentBinding extends Microservice {
  bindingId: string | null
  projectCode: string
  projectName: string
  environmentId: string | null
  environmentCode: string | null
  environmentName: string | null
}

export interface MicroserviceEnvironmentQuery extends MicroserviceQuery {
  projectId?: string
  environmentId?: string
}

/** 新增微服务表单的可选环境，选项自带项目和环境标识。 */
export interface MicroserviceEnvironmentOption {
  id: string
  projectId: string
  projectCode: string
  projectName: string
  code: string
  name: string
}

/**
 * 查询项目下的微服务。
 * 对应 GET /api/v1/projects/{projectId}/services。
 */
export async function fetchMicroservices(projectId: string, query: MicroserviceQuery = {}) {
  const response = await http.get<ApiResponse<PageResponse<Microservice>>>(
    `/api/v1/projects/${projectId}/services`,
    { params: query },
  )
  return response.data.data!
}

/**
 * 查询全局微服务分页列表，返回项目、环境与服务的关联展示信息。
 * 对应 GET /api/v1/microservices/environment-bindings。
 */
export async function fetchMicroserviceEnvironmentBindings(query: MicroserviceEnvironmentQuery = {}) {
  const response = await http.get<ApiResponse<PageResponse<MicroserviceEnvironmentBinding>>>(
    '/api/v1/microservices/environment-bindings',
    { params: query },
  )
  return response.data.data!
}

/**
 * 查询新增微服务时可选择的全部启用环境。
 * 对应 GET /api/v1/microservices/environment-options。
 */
export async function fetchMicroserviceEnvironmentOptions() {
  const response = await http.get<ApiResponse<MicroserviceEnvironmentOption[]>>(
    '/api/v1/microservices/environment-options',
  )
  return response.data.data!
}

/**
 * 查询微服务详情。
 */
export async function fetchMicroservice(projectId: string, id: string) {
  const response = await http.get<ApiResponse<Microservice>>(
    `/api/v1/projects/${projectId}/services/${id}`,
  )
  return response.data.data!
}

/**
 * 创建微服务。
 */
export async function createMicroservice(projectId: string, request: CreateMicroserviceRequest) {
  const response = await http.post<ApiResponse<Microservice>>(
    `/api/v1/projects/${projectId}/services`,
    request,
  )
  return response.data.data!
}

/**
 * 修改微服务基本信息。
 */
export async function updateMicroservice(
  projectId: string,
  id: string,
  request: UpdateMicroserviceRequest,
) {
  const response = await http.put<ApiResponse<Microservice>>(
    `/api/v1/projects/${projectId}/services/${id}`,
    request,
  )
  return response.data.data!
}

/**
 * 修改微服务状态。
 */
export async function updateMicroserviceStatus(
  projectId: string,
  id: string,
  status: MicroserviceStatus,
) {
  const response = await http.patch<ApiResponse<Microservice>>(
    `/api/v1/projects/${projectId}/services/${id}/status`,
    { status },
  )
  return response.data.data!
}

/**
 * 逻辑删除微服务。
 */
export async function deleteMicroservice(projectId: string, id: string) {
  await http.delete<ApiResponse<void>>(`/api/v1/projects/${projectId}/services/${id}`)
}

/**
 * 原子逻辑删除多个已停用微服务，并同步删除这些服务的全部环境关联。
 * 对应 DELETE /api/v1/microservices/batch。
 */
export async function deleteMicroservices(ids: string[]) {
  await http.delete<ApiResponse<void>>('/api/v1/microservices/batch', { data: { ids } })
}

/**
 * 查询指定环境下已关联的微服务。
 */
export async function fetchEnvironmentMicroservices(projectId: string, environmentId: string) {
  const response = await http.get<ApiResponse<Microservice[]>>(
    `/api/v1/projects/${projectId}/environments/${environmentId}/services`,
  )
  return response.data.data!
}

/**
 * 关联微服务和环境，重复调用保持幂等。
 */
export async function bindMicroserviceEnvironment(
  projectId: string,
  serviceId: string,
  environmentId: string,
) {
  await http.post<ApiResponse<void>>(
    `/api/v1/projects/${projectId}/services/${serviceId}/environments/${environmentId}`,
  )
}

/**
 * 批量关联同一项目下的多个环境，后端会整体校验并在同一事务中写入。
 * 对应 POST /api/v1/projects/{projectId}/services/{serviceId}/environments/batch。
 */
export async function bindMicroserviceEnvironments(
  projectId: string,
  serviceId: string,
  environmentIds: string[],
) {
  await http.post<ApiResponse<void>>(
    `/api/v1/projects/${projectId}/services/${serviceId}/environments/batch`,
    { environmentIds },
  )
}

/**
 * 解除微服务和环境的关联。
 */
export async function unbindMicroserviceEnvironment(
  projectId: string,
  serviceId: string,
  environmentId: string,
) {
  await http.delete<ApiResponse<void>>(
    `/api/v1/projects/${projectId}/services/${serviceId}/environments/${environmentId}`,
  )
}
