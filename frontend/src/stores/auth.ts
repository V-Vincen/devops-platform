import { defineStore } from 'pinia'
import { fetchCurrentUser, login, logout, type User } from '../api/auth'

const TOKEN_KEY = 'devops-access-token'
const USER_KEY = 'devops-current-user'

/**
 * 管理前端登录态，并统一维护令牌和当前用户缓存。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    user: readUser(),
    loading: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
  },
  actions: {
    async signIn(username: string, password: string) {
      this.loading = true
      try {
        const result = await login({ username, password })
        this.token = result.accessToken
        this.user = result.user
        localStorage.setItem(TOKEN_KEY, result.accessToken)
        localStorage.setItem(USER_KEY, JSON.stringify(result.user))
      } finally {
        this.loading = false
      }
    },

    async loadCurrentUser() {
      if (!this.token) {
        return
      }
      this.user = await fetchCurrentUser()
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },

    async signOut() {
      try {
        if (this.token) {
          await logout()
        }
      } finally {
        this.clear()
      }
    },

    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})

function readUser(): User | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as User
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}
