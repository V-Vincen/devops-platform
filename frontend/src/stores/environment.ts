import { defineStore } from 'pinia'
import {
  createEnvironment,
  deleteEnvironment,
  fetchEnvironment,
  fetchEnvironments,
  type CreateEnvironmentRequest,
  type Environment,
  type EnvironmentStatus,
  type UpdateEnvironmentRequest,
  updateEnvironment,
  updateEnvironmentStatus,
} from '../api/environment'

export const useEnvironmentStore = defineStore('environment', {
  state: () => ({
    records: [] as Environment[],
    total: 0,
    loading: false,
    current: 1,
    size: 10,
    keyword: '',
    status: '',
  }),
  actions: {
    async load(projectId: string) {
      this.loading = true
      try {
        const result = await fetchEnvironments(projectId, {
          keyword: this.keyword || undefined,
          status: this.status || undefined,
          current: this.current,
          size: this.size,
        })
        this.records = result.records
        this.total = result.total
      } finally {
        this.loading = false
      }
    },

    async add(projectId: string, request: CreateEnvironmentRequest) {
      const environment = await createEnvironment(projectId, request)
      this.current = 1
      await this.load(projectId)
      return environment
    },

    async getOne(projectId: string, id: string) {
      return fetchEnvironment(projectId, id)
    },

    async update(projectId: string, id: string, request: UpdateEnvironmentRequest) {
      const environment = await updateEnvironment(projectId, id, request)
      await this.load(projectId)
      return environment
    },

    async changeStatus(projectId: string, id: string, status: EnvironmentStatus) {
      const environment = await updateEnvironmentStatus(projectId, id, status)
      await this.load(projectId)
      return environment
    },

    async remove(projectId: string, id: string) {
      await deleteEnvironment(projectId, id)
      await this.load(projectId)
    },
  },
})
