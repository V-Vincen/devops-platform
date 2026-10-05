import { http, type ApiResponse } from './http'

export interface Environment {
  id: string
  projectId: string
  code: string
  name: string
  namespace: string
  clusterName: string | null
  status: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface EnvironmentQuery {
  /** 可选的项目上下文；用于从项目列表直接查看该项目的环境。 */
  projectId?: string
  keyword?: string
  status?: string
  current?: number
  size?: number
}

export interface CreateEnvironmentRequest {
  code: string
  name: string
  namespace: string
  clusterName?: string
  description?: string
}

export interface UpdateEnvironmentRequest {
  name: string
  namespace: string
  clusterName?: string
  description?: string
}

export type EnvironmentStatus = 'ACTIVE' | 'DISABLED'

/** 全局环境分页行，附带所属项目的展示信息。 */
export interface ProjectEnvironment extends Environment {
  projectCode: string
  projectName: string
}

export interface PageResponse<T> {
  records: T[]
  current: number
  size: number
  total: number
  pages: number
}

const basePath = (projectId: string) => `/api/v1/projects/${projectId}/environments`

/**
 * 查询项目下的环境分页列表。
 * 对应 GET /api/v1/projects/{projectId}/environments。
 */
export async function fetchEnvironments(projectId: string, query: EnvironmentQuery = {}) {
  const response = await http.get<ApiResponse<PageResponse<Environment>>>(basePath(projectId), {
    params: query,
  })
  return response.data.data!
}

/**
 * 查询当前用户可访问范围内的全局环境分页列表。
 * 对应 GET /api/v1/environments。
 */
export async function fetchProjectEnvironments(query: EnvironmentQuery = {}) {
  const response = await http.get<ApiResponse<PageResponse<ProjectEnvironment>>>('/api/v1/environments', {
    params: query,
  })
  return response.data.data!
}

/**
 * 查询环境详情，并校验环境属于指定项目。
 * 对应 GET /api/v1/projects/{projectId}/environments/{id}。
 */
export async function fetchEnvironment(projectId: string, id: string) {
  const response = await http.get<ApiResponse<Environment>>(`${basePath(projectId)}/${id}`)
  return response.data.data!
}

/**
 * 创建项目环境。
 * 对应 POST /api/v1/projects/{projectId}/environments。
 */
export async function createEnvironment(projectId: string, request: CreateEnvironmentRequest) {
  const response = await http.post<ApiResponse<Environment>>(basePath(projectId), request)
  return response.data.data!
}

/**
 * 修改环境名称、Namespace、集群和描述，环境编码不可修改。
 * 对应 PUT /api/v1/projects/{projectId}/environments/{id}。
 */
export async function updateEnvironment(projectId: string, id: string, request: UpdateEnvironmentRequest) {
  const response = await http.put<ApiResponse<Environment>>(`${basePath(projectId)}/${id}`, request)
  return response.data.data!
}

/**
 * 修改环境状态。
 * 对应 PATCH /api/v1/projects/{projectId}/environments/{id}/status。
 */
export async function updateEnvironmentStatus(projectId: string, id: string, status: EnvironmentStatus) {
  const response = await http.patch<ApiResponse<Environment>>(
    `${basePath(projectId)}/${id}/status`,
    { status },
  )
  return response.data.data!
}

/**
 * 逻辑删除环境。
 * 对应 DELETE /api/v1/projects/{projectId}/environments/{id}。
 */
export async function deleteEnvironment(projectId: string, id: string) {
  await http.delete<ApiResponse<void>>(`${basePath(projectId)}/${id}`)
}

/**
 * 原子逻辑删除多个已停用且未关联微服务的环境。
 * 对应 DELETE /api/v1/environments/batch。
 */
export async function deleteEnvironments(ids: string[]) {
  await http.delete<ApiResponse<void>>('/api/v1/environments/batch', { data: { ids } })
}
