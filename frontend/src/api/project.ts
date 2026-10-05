import { http, type ApiResponse } from './http'

export interface Project {
  // 后端主键可能是 64 位雪花 ID，使用字符串避免 JavaScript 数字精度丢失。
  id: string
  code: string
  name: string
  description: string | null
  status: string
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  records: T[]
  current: number
  size: number
  total: number
  pages: number
}

export interface ProjectQuery {
  keyword?: string
  status?: string
  current?: number
  size?: number
}

export interface CreateProjectRequest {
  code: string
  name: string
  description?: string
}

export interface UpdateProjectRequest {
  name: string
  description?: string
}

export type ProjectStatus = 'ACTIVE' | 'DISABLED'

/**
 * 查询项目分页列表。
 * 对应 GET /api/v1/projects。
 */
export async function fetchProjects(query: ProjectQuery = {}) {
  const response = await http.get<ApiResponse<PageResponse<Project>>>('/api/v1/projects', {
    params: query,
  })
  return response.data.data!
}

/**
 * 创建项目。
 * 对应 POST /api/v1/projects。
 */
export async function createProject(request: CreateProjectRequest) {
  const response = await http.post<ApiResponse<Project>>('/api/v1/projects', request)
  return response.data.data!
}

/**
 * 查询项目详情。
 * 对应 GET /api/v1/projects/{id}。
 */
export async function fetchProject(id: string) {
  const response = await http.get<ApiResponse<Project>>(`/api/v1/projects/${id}`)
  return response.data.data!
}

/**
 * 修改项目名称和描述，项目编码不可修改。
 * 对应 PUT /api/v1/projects/{id}。
 */
export async function updateProject(id: string, request: UpdateProjectRequest) {
  const response = await http.put<ApiResponse<Project>>(`/api/v1/projects/${id}`, request)
  return response.data.data!
}

/**
 * 修改项目状态。
 * 对应 PATCH /api/v1/projects/{id}/status。
 */
export async function updateProjectStatus(id: string, status: ProjectStatus) {
  const response = await http.patch<ApiResponse<Project>>(`/api/v1/projects/${id}/status`, { status })
  return response.data.data!
}

/**
 * 逻辑删除项目。
 * 对应 DELETE /api/v1/projects/{id}。
 */
export async function deleteProject(id: string) {
  await http.delete<ApiResponse<void>>(`/api/v1/projects/${id}`)
}

/**
 * 原子逻辑删除多个已停用且不存在下级资源的项目。
 * 对应 DELETE /api/v1/projects/batch。
 */
export async function deleteProjects(ids: string[]) {
  await http.delete<ApiResponse<void>>('/api/v1/projects/batch', { data: { ids } })
}
