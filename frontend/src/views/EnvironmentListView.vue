<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  Delete,
  EditPen,
  Monitor,
  Plus,
  RefreshLeft,
  Search,
  SwitchButton,
  View,
} from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createEnvironment,
  deleteEnvironments,
  fetchEnvironment,
  fetchProjectEnvironments,
  updateEnvironment,
  updateEnvironmentStatus,
  type Environment,
  type EnvironmentStatus,
  type ProjectEnvironment,
} from '../api/environment'
import { fetchProjects, type Project } from '../api/project'

type EnvironmentRow = ProjectEnvironment

const router = useRouter()
const route = useRoute()
const records = ref<EnvironmentRow[]>([])
const projects = ref<Project[]>([])
const loading = ref(false)
const submitting = ref(false)
const detailLoading = ref(false)
const batchDeleting = ref(false)
const actionLoadingId = ref<string | null>(null)
const selectedIds = ref<string[]>([])
const keyword = ref('')
const status = ref<'' | EnvironmentStatus>('')
const current = ref(1)
const size = ref(10)
const total = ref(0)
const createVisible = ref(false)
const editVisible = ref(false)
const detailVisible = ref(false)
const createFormRef = ref()
const editFormRef = ref()
const detailEnvironment = ref<Environment | null>(null)
const editProjectId = ref('')

const createForm = reactive({
  code: '',
  name: '',
  namespace: '',
  clusterName: '',
  description: '',
  projectId: '',
})

const editForm = reactive({
  id: '',
  code: '',
  name: '',
  namespace: '',
  clusterName: '',
  description: '',
})

const rules = {
  code: [{ required: true, message: '请输入环境编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入环境名称', trigger: 'blur' }],
  namespace: [{ required: true, message: '请输入 Namespace', trigger: 'blur' }],
  projectId: [{ required: true, message: '请选择关联项目', trigger: 'change' }],
}

const selectedRows = computed(() => records.value.filter((row) => selectedIds.value.includes(row.id)))
const isAllSelected = computed(
  () => records.value.length > 0 && records.value.every((row) => selectedIds.value.includes(row.id)),
)
const isAllIndeterminate = computed(
  () => selectedRows.value.length > 0 && selectedRows.value.length < records.value.length,
)
const projectRanges = computed(() => createRanges((row) => row.projectId))
const contextualProjectId = computed(() => {
  const value = route.query.projectId
  return typeof value === 'string' && value.trim() ? value : undefined
})
let latestRequestId = 0

/**
 * 服务端已按项目排序；当前页继续按连续项目区间合并，分页后不会保留上一页的选择状态。
 */
function createRanges(keyOf: (row: EnvironmentRow) => string) {
  const ranges = new Map<number, number>()
  let start = 0
  while (start < records.value.length) {
    const key = keyOf(records.value[start])
    let end = start + 1
    while (end < records.value.length && keyOf(records.value[end]) === key) {
      end += 1
    }
    ranges.set(start, end - start)
    start = end
  }
  return ranges
}

/** 一次请求完成当前用户有权访问的环境筛选和分页，避免逐项目拉取全部数据。 */
async function loadPage() {
  const requestId = ++latestRequestId
  loading.value = true
  try {
    const page = await fetchProjectEnvironments({
      projectId: contextualProjectId.value,
      keyword: keyword.value || undefined,
      status: status.value || undefined,
      current: current.value,
      size: size.value,
    })
    // 快速切换项目时，忽略先返回的旧请求，避免旧列表覆盖当前项目结果。
    if (requestId !== latestRequestId) {
      return
    }
    records.value = page.records
    current.value = page.current
    size.value = page.size
    total.value = page.total
    selectedIds.value = []
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false
    }
  }
}

/** 项目选项仅为新增环境表单服务，不跟随每次翻页重复请求。 */
async function loadProjectOptions() {
  projects.value = (await fetchProjects({ current: 1, size: 100 })).records
}

async function search() {
  current.value = 1
  await loadPage()
}

async function resetSearch() {
  keyword.value = ''
  status.value = ''
  current.value = 1
  await loadPage()
}

async function changePageSize() {
  current.value = 1
  await loadPage()
}

function rowsOfProject(projectId: string) {
  return records.value.filter((row) => row.projectId === projectId)
}

function isProjectChecked(projectId: string) {
  const rows = rowsOfProject(projectId)
  return rows.length > 0 && rows.every((row) => selectedIds.value.includes(row.id))
}

function isProjectIndeterminate(projectId: string) {
  const rows = rowsOfProject(projectId)
  const selectedCount = rows.filter((row) => selectedIds.value.includes(row.id)).length
  return selectedCount > 0 && selectedCount < rows.length
}

/** 一个项目组只显示一个复选框，勾选范围严格限定在当前分页结果。 */
function toggleProjectSelection(projectId: string, checked: boolean) {
  const groupIds = rowsOfProject(projectId).map((row) => row.id)
  const selected = new Set(selectedIds.value)
  if (checked) {
    groupIds.forEach((id) => selected.add(id))
  } else {
    groupIds.forEach((id) => selected.delete(id))
  }
  selectedIds.value = [...selected]
}

/** 表头全选只选择当前页，不会误操作其他分页数据。 */
function toggleAllSelection(checked: boolean) {
  selectedIds.value = checked ? records.value.map((row) => row.id) : []
}

/** 第一列复选框和第二列项目资料均按项目分组纵向合并。 */
function tableSpanMethod({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }) {
  if (columnIndex !== 0 && columnIndex !== 1) {
    return [1, 1]
  }
  const groupSize = projectRanges.value.get(rowIndex)
  return groupSize ? [groupSize, 1] : [0, 0]
}

function resetCreateForm() {
  Object.assign(createForm, {
    code: '', name: '', namespace: '', clusterName: '', description: '', projectId: '',
  })
}

function resetEditForm() {
  Object.assign(editForm, {
    id: '', code: '', name: '', namespace: '', clusterName: '', description: '',
  })
  editProjectId.value = ''
}

async function submitCreate() {
  await createFormRef.value.validate()
  submitting.value = true
  try {
    await createEnvironment(createForm.projectId, {
      code: createForm.code,
      name: createForm.name,
      namespace: createForm.namespace,
      clusterName: createForm.clusterName || undefined,
      description: createForm.description || undefined,
    })
    createVisible.value = false
    resetCreateForm()
    await loadPage()
    ElMessage.success('环境创建成功')
  } finally {
    submitting.value = false
  }
}

function openEdit(row: EnvironmentRow) {
  editProjectId.value = row.projectId
  Object.assign(editForm, {
    id: row.id,
    code: row.code,
    name: row.name,
    namespace: row.namespace,
    clusterName: row.clusterName ?? '',
    description: row.description ?? '',
  })
  editVisible.value = true
}

function openSelectedEdit() {
  if (selectedRows.value.length === 1) {
    openEdit(selectedRows.value[0])
  }
}

/** 环境入口复用微服务全局页，并通过查询参数固定当前项目和环境范围。 */
function openMicroservices(row: EnvironmentRow) {
  router.push({ path: '/microservices', query: { projectId: row.projectId, environmentId: row.id } })
}

async function submitEdit() {
  await editFormRef.value.validate()
  submitting.value = true
  try {
    await updateEnvironment(editProjectId.value, editForm.id, {
      name: editForm.name,
      namespace: editForm.namespace,
      clusterName: editForm.clusterName || undefined,
      description: editForm.description || undefined,
    })
    editVisible.value = false
    resetEditForm()
    await loadPage()
    ElMessage.success('环境修改成功')
  } finally {
    submitting.value = false
  }
}

async function openDetail(row: EnvironmentRow) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailEnvironment.value = await fetchEnvironment(row.projectId, row.id)
  } finally {
    detailLoading.value = false
  }
}

async function toggleStatus(row: EnvironmentRow) {
  const targetStatus: EnvironmentStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionText = targetStatus === 'DISABLED' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${actionText}环境“${row.name}”吗？`,
      `${actionText}环境`,
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  actionLoadingId.value = row.id
  try {
    await updateEnvironmentStatus(row.projectId, row.id, targetStatus)
    await loadPage()
    ElMessage.success(`环境已${actionText}`)
  } finally {
    actionLoadingId.value = null
  }
}

/** 服务端会先校验整批状态与关联，再原子删除，页面只提交当前页选中的环境。 */
async function removeEnvironments(rows: EnvironmentRow[] = selectedRows.value) {
  if (rows.length === 0) {
    ElMessage.warning('请先选择需要删除的环境')
    return
  }
  if (rows.some((row) => row.status === 'ACTIVE')) {
    ElMessage.warning('选中环境包含启用状态数据，请先全部停用')
    return
  }
  try {
    await ElMessageBox.confirm(
      `删除后选中的 ${rows.length} 个环境将从默认列表隐藏，确定继续吗？`,
      '批量删除环境',
      { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    await deleteEnvironments(rows.map((row) => row.id))
    selectedIds.value = []
    await loadPage()
    ElMessage.success(`已删除 ${rows.length} 个环境`)
  } finally {
    batchDeleting.value = false
  }
}

function formatDateTime(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(async () => {
  await Promise.all([loadProjectOptions(), loadPage()])
})

watch(
  () => route.query.projectId,
  async () => {
    current.value = 1
    await loadPage()
  },
)
</script>

<template>
  <section class="page-section">
    <div class="page-heading compact-page-heading">
      <div>
        <p class="eyebrow">ENVIRONMENTS</p>
        <div class="page-title-row">
          <h1>环境管理</h1>
          <span class="page-title-divider" />
          <p class="page-description">按项目分组展示全部环境，并维护 Namespace 与集群信息。</p>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-layout">
        <div class="filter-fields">
          <label class="filter-field">
            <span>环境关键字</span>
            <el-input v-model="keyword" clearable placeholder="编码、名称或 Namespace" @keyup.enter="search" />
          </label>
          <label class="filter-field">
            <span>环境状态</span>
            <el-select v-model="status" clearable placeholder="全部状态">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </label>
        </div>
        <div class="filter-actions">
          <el-button size="small" class="action-button action-search" :icon="Search" @click="search">搜索</el-button>
          <el-button size="small" class="action-button action-reset" :icon="RefreshLeft" @click="resetSearch">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-actions">
          <el-button size="small" class="action-button action-create" :icon="Plus" @click="createVisible = true">新增</el-button>
          <el-button size="small" class="action-button action-edit" :icon="EditPen" :disabled="selectedRows.length !== 1" @click="openSelectedEdit">修改</el-button>
          <el-button size="small" class="action-button action-delete" :icon="Delete" :loading="batchDeleting" :disabled="selectedRows.length === 0" @click="removeEnvironments()">删除</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" row-key="id" border :span-method="tableSpanMethod">
        <el-table-column label="" fixed="left" width="56" align="center" :resizable="false">
          <template #header>
            <el-checkbox :model-value="isAllSelected" :indeterminate="isAllIndeterminate" aria-label="选择当前页全部环境" @change="toggleAllSelection(Boolean($event))" />
          </template>
          <template #default="{ row }">
            <el-checkbox :model-value="isProjectChecked(row.projectId)" :indeterminate="isProjectIndeterminate(row.projectId)" :aria-label="`选择项目 ${row.projectName} 下的全部环境`" @change="toggleProjectSelection(row.projectId, Boolean($event))" />
          </template>
        </el-table-column>
        <el-table-column label="项目" min-width="220">
          <template #default="{ row }">
            <div class="project-group-cell">
              <div class="project-group-name">{{ row.projectName }}</div>
              <div class="project-group-code">{{ row.projectCode }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="环境编码" min-width="140" />
        <el-table-column prop="name" label="环境名称" min-width="160" />
        <el-table-column prop="namespace" label="Namespace" min-width="180" />
        <el-table-column prop="clusterName" label="集群" min-width="140"><template #default="{ row }">{{ row.clusterName || '-' }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" width="345" :resizable="false" class-name="operation-column">
          <template #default="{ row }">
            <el-button size="small" class="table-action-button action-detail" :icon="View" @click="openDetail(row)">详情</el-button>
            <el-button size="small" class="table-action-button action-edit" :icon="EditPen" @click="openEdit(row)">修改</el-button>
            <el-button size="small" class="table-action-button action-primary" :icon="Monitor" @click="openMicroservices(row)">微服务</el-button>
            <el-button size="small" class="table-action-button" :class="row.status === 'ACTIVE' ? 'action-warning' : 'action-enable'" :icon="SwitchButton" :loading="actionLoadingId === row.id" @click="toggleStatus(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
            <el-button size="small" class="table-action-button action-delete" :icon="Delete" :disabled="row.status === 'ACTIVE'" :loading="batchDeleting" @click="removeEnvironments([row])">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination v-model:current-page="current" v-model:page-size="size" background layout="total, sizes, prev, pager, next" :total="total" @current-change="loadPage" @size-change="changePageSize" />
      </div>
    </el-card>

    <el-dialog v-model="createVisible" title="新建环境" width="560px" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="rules" label-width="100px">
        <el-form-item label="环境编码" prop="code"><el-input v-model="createForm.code" placeholder="例如 test" /></el-form-item>
        <el-form-item label="环境名称" prop="name"><el-input v-model="createForm.name" placeholder="例如 测试环境" /></el-form-item>
        <el-form-item label="Namespace" prop="namespace"><el-input v-model="createForm.namespace" placeholder="例如 devops-test" /></el-form-item>
        <el-form-item label="集群名称" prop="clusterName"><el-input v-model="createForm.clusterName" placeholder="单集群可填写 local" /></el-form-item>
        <el-form-item label="环境描述" prop="description"><el-input v-model="createForm.description" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="关联项目" prop="projectId"><el-select v-model="createForm.projectId" filterable placeholder="请选择启用中的项目" class="form-control"><el-option v-for="project in projects" :key="project.id" :label="`${project.name}（${project.code}）`" :value="project.id" :disabled="project.status !== 'ACTIVE'" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑环境" width="560px" @closed="resetEditForm">
      <el-form ref="editFormRef" :model="editForm" :rules="rules" label-width="100px">
        <el-form-item label="环境编码"><el-input v-model="editForm.code" disabled /></el-form-item>
        <el-form-item label="环境名称" prop="name"><el-input v-model="editForm.name" placeholder="请输入环境名称" /></el-form-item>
        <el-form-item label="Namespace" prop="namespace"><el-input v-model="editForm.namespace" placeholder="请输入 Namespace" /></el-form-item>
        <el-form-item label="集群名称" prop="clusterName"><el-input v-model="editForm.clusterName" placeholder="单集群可填写 local" /></el-form-item>
        <el-form-item label="环境描述" prop="description"><el-input v-model="editForm.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="环境详情" size="480px">
      <div v-loading="detailLoading">
        <template v-if="detailEnvironment">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="环境编码">{{ detailEnvironment.code }}</el-descriptions-item>
            <el-descriptions-item label="环境名称">{{ detailEnvironment.name }}</el-descriptions-item>
            <el-descriptions-item label="Namespace">{{ detailEnvironment.namespace }}</el-descriptions-item>
            <el-descriptions-item label="集群名称">{{ detailEnvironment.clusterName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="detailEnvironment.status === 'ACTIVE' ? 'success' : 'info'">{{ detailEnvironment.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="环境描述">{{ detailEnvironment.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(detailEnvironment.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDateTime(detailEnvironment.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </section>
</template>
