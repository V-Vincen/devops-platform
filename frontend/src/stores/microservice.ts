import { defineStore } from 'pinia'
import {
  bindMicroserviceEnvironment,
  createMicroservice,
  deleteMicroservice,
  fetchEnvironmentMicroservices,
  fetchMicroservice,
  fetchMicroservices,
  type CreateMicroserviceRequest,
  type Microservice,
  type MicroserviceStatus,
  type UpdateMicroserviceRequest,
  unbindMicroserviceEnvironment,
  updateMicroservice,
  updateMicroserviceStatus,
} from '../api/microservice'

/**
 * 微服务页面状态和项目、环境关联操作。
 */
export const useMicroserviceStore = defineStore('microservice', {
  state: () => ({
    records: [] as Microservice[],
    total: 0,
    loading: false,
    current: 1,
    size: 10,
    keyword: '',
    status: '' as '' | MicroserviceStatus,
  }),
  actions: {
    async load(projectId: string) {
      this.loading = true
      try {
        const result = await fetchMicroservices(projectId, {
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

    async loadByEnvironment(projectId: string, environmentId: string) {
      this.loading = true
      try {
        this.records = await fetchEnvironmentMicroservices(projectId, environmentId)
        this.total = this.records.length
      } finally {
        this.loading = false
      }
    },

    async add(projectId: string, request: CreateMicroserviceRequest) {
      const service = await createMicroservice(projectId, request)
      await this.load(projectId)
      return service
    },

    async getOne(projectId: string, id: string) {
      return fetchMicroservice(projectId, id)
    },

    async update(projectId: string, id: string, request: UpdateMicroserviceRequest) {
      const service = await updateMicroservice(projectId, id, request)
      await this.load(projectId)
      return service
    },

    async changeStatus(projectId: string, id: string, status: MicroserviceStatus) {
      const service = await updateMicroserviceStatus(projectId, id, status)
      await this.load(projectId)
      return service
    },

    async remove(projectId: string, id: string) {
      await deleteMicroservice(projectId, id)
      await this.load(projectId)
    },

    async bind(projectId: string, serviceId: string, environmentId: string) {
      await bindMicroserviceEnvironment(projectId, serviceId, environmentId)
    },

    async unbind(projectId: string, serviceId: string, environmentId: string) {
      await unbindMicroserviceEnvironment(projectId, serviceId, environmentId)
    },
  },
})
