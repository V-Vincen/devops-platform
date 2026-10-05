import axios from 'axios'
import { ElMessage } from 'element-plus'

export interface ApiResponse<T> {
  success: boolean
  data: T | null
  message: string
}

export const http = axios.create({
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('devops-access-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error.response?.data?.message ?? '请求失败，请稍后重试'
    ElMessage.error(message)
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      localStorage.removeItem('devops-access-token')
      localStorage.removeItem('devops-current-user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)
