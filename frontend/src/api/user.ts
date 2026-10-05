import { http, type ApiResponse } from './http'

/** 平台用户角色：管理员拥有全局资源权限，开发人员按项目授权。 */
export type UserRole = 'ADMIN' | 'DEVELOPER'

/** 不包含密码摘要的用户信息。 */
export interface PlatformUser {
  id: string
  username: string
  displayName: string
  role: UserRole
  status: string
  createdAt: string
  updatedAt: string
}

export interface CreateUserRequest {
  username: string
  displayName: string
  password: string
  role: UserRole
}

/** 查询平台用户列表，仅管理员可访问。 */
export async function fetchUsers() {
  const response = await http.get<ApiResponse<PlatformUser[]>>('/api/v1/users')
  return response.data.data ?? []
}

/** 创建内部平台账号；平台不提供面向外部人员的自助注册。 */
export async function createUser(request: CreateUserRequest) {
  const response = await http.post<ApiResponse<PlatformUser>>('/api/v1/users', request)
  return response.data.data!
}

/** 查询开发人员已被显式授予访问权限的项目编号。 */
export async function fetchGrantedProjectIds(userId: string) {
  const response = await http.get<ApiResponse<string[]>>(`/api/v1/users/${userId}/projects`)
  return response.data.data ?? []
}

/** 向开发人员授予一个项目的访问权限，重复提交保持幂等。 */
export async function grantProject(userId: string, projectId: string) {
  await http.post<ApiResponse<void>>(`/api/v1/users/${userId}/projects/${projectId}`)
}

/** 解除开发人员对指定项目的访问权限。 */
export async function revokeProject(userId: string, projectId: string) {
  await http.delete<ApiResponse<void>>(`/api/v1/users/${userId}/projects/${projectId}`)
}
