import { http, type ApiResponse } from './http'

export interface User {
  id: string
  username: string
  displayName: string
  role: 'ADMIN' | 'DEVELOPER'
  status: 'ACTIVE' | 'DISABLED'
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: User
}

/**
 * 用户登录。
 * 对应 POST /api/v1/auth/login。
 */
export async function login(request: LoginRequest) {
  const response = await http.post<ApiResponse<LoginResponse>>('/api/v1/auth/login', request)
  return response.data.data!
}

/**
 * 查询当前用户。
 * 对应 GET /api/v1/auth/me。
 */
export async function fetchCurrentUser() {
  const response = await http.get<ApiResponse<User>>('/api/v1/auth/me')
  return response.data.data!
}

/**
 * 注销当前登录令牌。
 * 对应 POST /api/v1/auth/logout。
 */
export async function logout() {
  await http.post<ApiResponse<void>>('/api/v1/auth/logout')
}
