import { defineStore } from 'pinia'
import {
  createProject,
  deleteProject,
  fetchProject,
  fetchProjects,
  type CreateProjectRequest,
  type Project,
  type ProjectStatus,
  type UpdateProjectRequest,
  updateProject,
  updateProjectStatus,
} from '../api/project'

export const useProjectStore = defineStore('project', {
  state: () => ({
    records: [] as Project[],
    total: 0,
    loading: false,
    current: 1,
    size: 10,
    keyword: '',
    status: '',
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        const result = await fetchProjects({
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

    async add(request: CreateProjectRequest) {
      const project = await createProject(request)
      // 新建成功后回到第一页，确保用户能立即看到刚刚创建的项目。
      this.current = 1
      await this.load()
      return project
    },

    async getOne(id: string) {
      return fetchProject(id)
    },

    async update(id: string, request: UpdateProjectRequest) {
      const project = await updateProject(id, request)
      await this.load()
      return project
    },

    async changeStatus(id: string, status: ProjectStatus) {
      const project = await updateProjectStatus(id, status)
      await this.load()
      return project
    },

    async remove(id: string) {
      await deleteProject(id)
      await this.load()
    },
  },
})
