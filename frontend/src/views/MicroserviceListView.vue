<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  Delete,
  EditPen,
  Plus,
  RefreshLeft,
  Search,
  SwitchButton,
  View,
} from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createMicroservice,
  deleteMicroservices,
  fetchMicroservice,
  fetchMicroserviceEnvironmentBindings,
  fetchMicroserviceEnvironmentOptions,
  updateMicroservice,
  updateMicroserviceStatus,
  type Microservice,
  type MicroserviceEnvironmentBinding,
  type MicroserviceEnvironmentOption,
  type MicroserviceStatus,
} from '../api/microservice'

type MicroserviceRow = MicroserviceEnvironmentBinding

const route = useRoute()
const records = ref<MicroserviceRow[]>([])
const environmentOptions = ref<MicroserviceEnvironmentOption[]>([])
const loading = ref(false)
const optionLoading = ref(false)
const submitting = ref(false)
const detailLoading = ref(false)
const batchDeleting = ref(false)
const actionLoadingId = ref<string | null>(null)
const selectedBindingKeys = ref<string[]>([])
const keyword = ref('')
const status = ref<'' | MicroserviceStatus>('')
const current = ref(1)
const size = ref(10)
const total = ref(0)
const createVisible = ref(false)
const editVisible = ref(false)
const detailVisible = ref(false)
const createFormRef = ref()
const editFormRef = ref()
const detailService = ref<Microservice | null>(null)

const createForm = reactive({
  serviceCode: '',
  serviceName: '',
  repositoryUrl: '',
  branchName: 'main',
  buildType: 'MAVEN',
  imageRepository: '',
  port: 8080,
  replicas: 1,
  cpuLimit: '',
  memoryLimit: '',
  description: '',
  environmentIds: [] as string[],
})

const editForm = reactive({
  id: '',
  projectId: '',
  serviceCode: '',
  serviceName: '',
  repositoryUrl: '',
  branchName: 'main',
  buildType: 'MAVEN',
  imageRepository: '',
  port: 8080,
  replicas: 1,
  cpuLimit: '',
  memoryLimit: '',
  description: '',
})

const rules = {
  serviceCode: [{ required: true, message: '请输入微服务编码', trigger: 'blur' }],
  serviceName: [{ required: true, message: '请输入微服务名称', trigger: 'blur' }],
  repositoryUrl: [{ required: true, message: '请输入代码仓库地址', trigger: 'blur' }],
  branchName: [{ required: true, message: '请输入构建分支', trigger: 'blur' }],
  buildType: [{ required: true, message: '请选择构建类型', trigger: 'change' }],
  imageRepository: [{ required: true, message: '请输入镜像仓库地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入服务端口', trigger: 'change' }],
  replicas: [{ required: true, message: '请输入副本数量', trigger: 'change' }],
  environmentIds: [{ required: true, type: 'array', min: 1, message: '请至少选择一个关联环境', trigger: 'change' }],
}

function queryValue(value: unknown) {
  return typeof value === 'string' ? value : ''
}

const fixedProjectId = computed(() => queryValue(route.params.projectId) || queryValue(route.query.projectId))
const fixedEnvironmentId = computed(() => queryValue(route.params.environmentId) || queryValue(route.query.environmentId))
const selectedRows = computed(() => records.value.filter((row) => selectedBindingKeys.value.includes(bindingKey(row))))
const selectedServices = computed(() => {
  const services = new Map<string, MicroserviceRow>()
  selectedRows.value.forEach((row) => services.set(row.id, row))
  return [...services.values()]
})
const isAllSelected = computed(
  () => records.value.length > 0 && records.value.every((row) => selectedBindingKeys.value.includes(bindingKey(row))),
)
const isAllIndeterminate = computed(
  () => selectedRows.value.length > 0 && selectedRows.value.length < records.value.length,
)
const projectRanges = computed(() => createRanges((row) => row.projectId))
const environmentRanges = computed(() => createRanges(environmentGroupKey))
const selectedEnvironmentProjectId = computed(() => {
  const selected = environmentOptions.value.find((option) => createForm.environmentIds.includes(option.id))
  return selected?.projectId ?? ''
})

/** 每个服务环境关系拥有独立选择键，未关联环境的服务也能被安全选择。 */
function bindingKey(row: MicroserviceRow) {
  return row.bindingId || `unbound-${row.id}`
}

/** 未关联环境的服务各自独立成组，防止全部未关联服务被错误合并。 */
function environmentGroupKey(row: MicroserviceRow) {
  return `${row.projectId}:${row.environmentId || `unbound-${row.id}`}`
}

function createRanges(keyOf: (row: MicroserviceRow) => string) {
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

/** 服务端完成筛选、权限范围和分页，前端只对当前页执行分组合并与选择。 */
async function loadPage() {
  loading.value = true
  try {
    const page = await fetchMicroserviceEnvironmentBindings({
      projectId: fixedProjectId.value || undefined,
      environmentId: fixedEnvironmentId.value || undefined,
      keyword: keyword.value || undefined,
      status: status.value || undefined,
      current: current.value,
      size: size.value,
    })
    records.value = page.records
    current.value = page.current
    size.value = page.size
    total.value = page.total
    selectedBindingKeys.value = []
  } finally {
    loading.value = false
  }
}

/** 下拉选项由后端一次性返回，避免为每个项目、环境重复发起浏览器请求。 */
async function loadEnvironmentOptions() {
  optionLoading.value = true
  try {
    environmentOptions.value = await fetchMicroserviceEnvironmentOptions()
  } finally {
    optionLoading.value = false
  }
}

async function openCreate() {
  await loadEnvironmentOptions()
  createVisible.value = true
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

function rowsOfEnvironment(groupKey: string) {
  return records.value.filter((row) => environmentGroupKey(row) === groupKey)
}

function isEnvironmentChecked(groupKey: string) {
  const rows = rowsOfEnvironment(groupKey)
  return rows.length > 0 && rows.every((row) => selectedBindingKeys.value.includes(bindingKey(row)))
}

function isEnvironmentIndeterminate(groupKey: string) {
  const rows = rowsOfEnvironment(groupKey)
  const selectedCount = rows.filter((row) => selectedBindingKeys.value.includes(bindingKey(row))).length
  return selectedCount > 0 && selectedCount < rows.length
}

/** 环境组复选框只选择该环境下的微服务关系，批量删除时会由服务编号去重。 */
function toggleEnvironmentSelection(groupKey: string, checked: boolean) {
  const keys = rowsOfEnvironment(groupKey).map(bindingKey)
  const selected = new Set(selectedBindingKeys.value)
  if (checked) {
    keys.forEach((key) => selected.add(key))
  } else {
    keys.forEach((key) => selected.delete(key))
  }
  selectedBindingKeys.value = [...selected]
}

/** 表头全选仅作用于当前分页数据，防止跨页误删。 */
function toggleAllSelection(checked: boolean) {
  selectedBindingKeys.value = checked ? records.value.map(bindingKey) : []
}

/** 第一列和环境列按环境分组合并，项目列按项目分组合并。 */
function tableSpanMethod({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }) {
  if (columnIndex === 0 || columnIndex === 2) {
    const groupSize = environmentRanges.value.get(rowIndex)
    return groupSize ? [groupSize, 1] : [0, 0]
  }
  if (columnIndex === 1) {
    const groupSize = projectRanges.value.get(rowIndex)
    return groupSize ? [groupSize, 1] : [0, 0]
  }
  return [1, 1]
}

function environmentLabel(option: MicroserviceEnvironmentOption) {
  return `${option.projectName}（${option.projectCode}）/ ${option.name}（${option.code}）`
}

/** 选择首个环境后，其他项目选项禁用；服务端仍会再次校验项目归属。 */
function isEnvironmentDisabled(option: MicroserviceEnvironmentOption) {
  return Boolean(selectedEnvironmentProjectId.value) && selectedEnvironmentProjectId.value !== option.projectId
}

function resetCreateForm() {
  Object.assign(createForm, {
    serviceCode: '', serviceName: '', repositoryUrl: '', branchName: 'main', buildType: 'MAVEN',
    imageRepository: '', port: 8080, replicas: 1, cpuLimit: '', memoryLimit: '', description: '', environmentIds: [],
  })
}

function resetEditForm() {
  Object.assign(editForm, {
    id: '', projectId: '', serviceCode: '', serviceName: '', repositoryUrl: '', branchName: 'main',
    buildType: 'MAVEN', imageRepository: '', port: 8080, replicas: 1, cpuLimit: '', memoryLimit: '', description: '',
  })
}

function buildRequest(form: typeof createForm) {
  return {
    serviceCode: form.serviceCode,
    serviceName: form.serviceName,
    repositoryUrl: form.repositoryUrl,
    branchName: form.branchName,
    buildType: form.buildType,
    imageRepository: form.imageRepository,
    port: form.port,
    replicas: form.replicas,
    cpuLimit: form.cpuLimit || undefined,
    memoryLimit: form.memoryLimit || undefined,
    description: form.description || undefined,
    environmentIds: form.environmentIds,
  }
}

async function submitCreate() {
  await createFormRef.value.validate()
  const selectedOptions = environmentOptions.value.filter((option) => createForm.environmentIds.includes(option.id))
  const projectIds = new Set(selectedOptions.map((option) => option.projectId))
  if (selectedOptions.length !== createForm.environmentIds.length || projectIds.size !== 1) {
    ElMessage.warning('关联环境必须全部属于同一个项目，请重新选择')
    return
  }

  submitting.value = true
  try {
    await createMicroservice(selectedOptions[0].projectId, buildRequest(createForm))
    createVisible.value = false
    resetCreateForm()
    current.value = 1
    await loadPage()
    ElMessage.success('微服务创建并关联环境成功')
  } finally {
    submitting.value = false
  }
}

function openEdit(row: MicroserviceRow) {
  Object.assign(editForm, {
    id: row.id,
    projectId: row.projectId,
    serviceCode: row.serviceCode,
    serviceName: row.serviceName,
    repositoryUrl: row.repositoryUrl,
    branchName: row.branchName,
    buildType: row.buildType,
    imageRepository: row.imageRepository,
    port: row.port,
    replicas: row.replicas,
    cpuLimit: row.cpuLimit ?? '',
    memoryLimit: row.memoryLimit ?? '',
    description: row.description ?? '',
  })
  editVisible.value = true
}

function openSelectedEdit() {
  if (selectedServices.value.length === 1) {
    openEdit(selectedServices.value[0])
  }
}

async function submitEdit() {
  await editFormRef.value.validate()
  submitting.value = true
  try {
    await updateMicroservice(editForm.projectId, editForm.id, {
      serviceName: editForm.serviceName,
      repositoryUrl: editForm.repositoryUrl,
      branchName: editForm.branchName,
      buildType: editForm.buildType,
      imageRepository: editForm.imageRepository,
      port: editForm.port,
      replicas: editForm.replicas,
      cpuLimit: editForm.cpuLimit || undefined,
      memoryLimit: editForm.memoryLimit || undefined,
      description: editForm.description || undefined,
    })
    editVisible.value = false
    resetEditForm()
    await loadPage()
    ElMessage.success('微服务修改成功')
  } finally {
    submitting.value = false
  }
}

async function openDetail(row: MicroserviceRow) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailService.value = await fetchMicroservice(row.projectId, row.id)
  } finally {
    detailLoading.value = false
  }
}

async function toggleStatus(row: MicroserviceRow) {
  const targetStatus: MicroserviceStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionText = targetStatus === 'DISABLED' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${actionText}微服务“${row.serviceName}”吗？`, `${actionText}微服务`, {
      type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消',
    })
  } catch {
    return
  }
  actionLoadingId.value = row.id
  try {
    await updateMicroserviceStatus(row.projectId, row.id, targetStatus)
    await loadPage()
    ElMessage.success(`微服务已${actionText}`)
  } finally {
    actionLoadingId.value = null
  }
}

/** 删除服务会同步删除服务在全部环境中的关联，重复选中同一服务时仅提交一次编号。 */
async function removeServices(rows: MicroserviceRow[] = selectedServices.value) {
  const uniqueRows = [...new Map(rows.map((row) => [row.id, row])).values()]
  if (uniqueRows.length === 0) {
    ElMessage.warning('请先选择需要删除的微服务')
    return
  }
  if (uniqueRows.some((row) => row.status === 'ACTIVE')) {
    ElMessage.warning('选中微服务包含启用状态数据，请先全部停用')
    return
  }
  try {
    await ElMessageBox.confirm(
      `删除后选中的 ${uniqueRows.length} 个微服务及其全部环境关联将从默认列表隐藏，确定继续吗？`,
      '批量删除微服务',
      { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    await deleteMicroservices(uniqueRows.map((row) => row.id))
    selectedBindingKeys.value = []
    await loadPage()
    ElMessage.success(`已删除 ${uniqueRows.length} 个微服务`)
  } finally {
    batchDeleting.value = false
  }
}

function formatDateTime(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(loadPage)

/** 同一组件收到项目或环境入口的新查询参数时，重新加载对应的分页范围。 */
watch(() => route.fullPath, async () => {
  current.value = 1
  await loadPage()
})
</script>

<template>
  <section class="page-section">
    <div class="page-heading compact-page-heading">
      <div>
        <p class="eyebrow">MICROSERVICES</p>
        <div class="page-title-row">
          <h1>微服务管理</h1>
          <span class="page-title-divider" />
          <p class="page-description">按项目和环境分组维护代码仓库、构建参数、镜像地址与运行资源。</p>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-layout">
        <div class="filter-fields">
          <label class="filter-field">
            <span>服务关键字</span>
            <el-input v-model="keyword" clearable placeholder="编码、名称或仓库地址" @keyup.enter="search" />
          </label>
          <label class="filter-field">
            <span>服务状态</span>
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
          <el-button size="small" class="action-button action-create" :icon="Plus" :loading="optionLoading" @click="openCreate">新增</el-button>
          <el-button size="small" class="action-button action-edit" :icon="EditPen" :disabled="selectedServices.length !== 1" @click="openSelectedEdit">修改</el-button>
          <el-button size="small" class="action-button action-delete" :icon="Delete" :disabled="selectedServices.length === 0" :loading="batchDeleting" @click="removeServices()">删除</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" :span-method="tableSpanMethod" :row-key="bindingKey" border>
        <el-table-column label="" fixed="left" width="56" align="center" :resizable="false">
          <template #header>
            <el-checkbox :model-value="isAllSelected" :indeterminate="isAllIndeterminate" aria-label="选择当前页全部微服务" @change="toggleAllSelection(Boolean($event))" />
          </template>
          <template #default="{ row }">
            <el-checkbox :model-value="isEnvironmentChecked(environmentGroupKey(row))" :indeterminate="isEnvironmentIndeterminate(environmentGroupKey(row))" :aria-label="`选择环境 ${row.environmentName || '未关联环境'} 下的全部微服务`" @change="toggleEnvironmentSelection(environmentGroupKey(row), Boolean($event))" />
          </template>
        </el-table-column>
        <el-table-column label="项目" min-width="220">
          <template #default="{ row }"><div class="project-group-cell"><div class="project-group-name">{{ row.projectName }}</div><div class="project-group-code">{{ row.projectCode }}</div></div></template>
        </el-table-column>
        <el-table-column label="环境" min-width="220">
          <template #default="{ row }"><div class="project-group-cell"><div class="project-group-name">{{ row.environmentName || '未关联环境' }}</div><div class="project-group-code">{{ row.environmentCode || '-' }}</div></div></template>
        </el-table-column>
        <el-table-column prop="serviceCode" label="服务编码" min-width="155" />
        <el-table-column prop="serviceName" label="服务名称" min-width="165" />
        <el-table-column prop="buildType" label="构建类型" min-width="110" />
        <el-table-column prop="branchName" label="分支" min-width="120" />
        <el-table-column prop="repositoryUrl" label="代码仓库" min-width="240" show-overflow-tooltip />
        <el-table-column prop="imageRepository" label="镜像仓库" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" width="270" :resizable="false" class-name="operation-column">
          <template #default="{ row }">
            <el-button size="small" class="table-action-button action-detail" :icon="View" @click="openDetail(row)">详情</el-button>
            <el-button size="small" class="table-action-button action-edit" :icon="EditPen" @click="openEdit(row)">修改</el-button>
            <el-button size="small" class="table-action-button" :class="row.status === 'ACTIVE' ? 'action-warning' : 'action-enable'" :icon="SwitchButton" :loading="actionLoadingId === row.id" @click="toggleStatus(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
            <el-button size="small" class="table-action-button action-delete" :icon="Delete" :disabled="row.status === 'ACTIVE'" :loading="batchDeleting" @click="removeServices([row])">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination v-model:current-page="current" v-model:page-size="size" background layout="total, sizes, prev, pager, next" :total="total" @current-change="loadPage" @size-change="changePageSize" />
      </div>
    </el-card>

    <el-dialog v-model="createVisible" title="新建微服务" width="680px" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="rules" label-width="112px">
        <el-form-item label="服务编码" prop="serviceCode"><el-input v-model="createForm.serviceCode" placeholder="例如 order-service" /></el-form-item>
        <el-form-item label="服务名称" prop="serviceName"><el-input v-model="createForm.serviceName" placeholder="例如 订单服务" /></el-form-item>
        <el-form-item label="代码仓库" prop="repositoryUrl"><el-input v-model="createForm.repositoryUrl" placeholder="例如 https://gitee.com/org/order-service" /></el-form-item>
        <el-form-item label="构建分支" prop="branchName"><el-input v-model="createForm.branchName" placeholder="例如 main" /></el-form-item>
        <el-form-item label="构建类型" prop="buildType"><el-select v-model="createForm.buildType" class="form-control"><el-option label="Maven" value="MAVEN" /><el-option label="Node.js" value="NODE_JS" /><el-option label="Gradle" value="GRADLE" /><el-option label="其他" value="OTHER" /></el-select></el-form-item>
        <el-form-item label="镜像仓库" prop="imageRepository"><el-input v-model="createForm.imageRepository" placeholder="例如 nexus.local/test/order-service" /></el-form-item>
        <el-form-item label="服务端口" prop="port"><el-input-number v-model="createForm.port" :min="1" :max="65535" class="form-control" /></el-form-item>
        <el-form-item label="副本数量" prop="replicas"><el-input-number v-model="createForm.replicas" :min="1" :max="20" class="form-control" /></el-form-item>
        <el-form-item label="CPU 上限"><el-input v-model="createForm.cpuLimit" placeholder="例如 500m" /></el-form-item>
        <el-form-item label="内存上限"><el-input v-model="createForm.memoryLimit" placeholder="例如 512Mi" /></el-form-item>
        <el-form-item label="服务描述"><el-input v-model="createForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="关联环境" prop="environmentIds">
          <el-select v-model="createForm.environmentIds" multiple filterable clearable class="form-control" placeholder="请选择同一项目下的一个或多个环境">
            <el-option v-for="option in environmentOptions" :key="option.id" :label="environmentLabel(option)" :value="option.id" :disabled="isEnvironmentDisabled(option)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑微服务" width="680px" @closed="resetEditForm">
      <el-form ref="editFormRef" :model="editForm" :rules="rules" label-width="112px">
        <el-form-item label="服务编码"><el-input v-model="editForm.serviceCode" disabled /></el-form-item>
        <el-form-item label="服务名称" prop="serviceName"><el-input v-model="editForm.serviceName" /></el-form-item>
        <el-form-item label="代码仓库" prop="repositoryUrl"><el-input v-model="editForm.repositoryUrl" /></el-form-item>
        <el-form-item label="构建分支" prop="branchName"><el-input v-model="editForm.branchName" /></el-form-item>
        <el-form-item label="构建类型" prop="buildType"><el-select v-model="editForm.buildType" class="form-control"><el-option label="Maven" value="MAVEN" /><el-option label="Node.js" value="NODE_JS" /><el-option label="Gradle" value="GRADLE" /><el-option label="其他" value="OTHER" /></el-select></el-form-item>
        <el-form-item label="镜像仓库" prop="imageRepository"><el-input v-model="editForm.imageRepository" /></el-form-item>
        <el-form-item label="服务端口" prop="port"><el-input-number v-model="editForm.port" :min="1" :max="65535" class="form-control" /></el-form-item>
        <el-form-item label="副本数量" prop="replicas"><el-input-number v-model="editForm.replicas" :min="1" :max="20" class="form-control" /></el-form-item>
        <el-form-item label="CPU 上限"><el-input v-model="editForm.cpuLimit" /></el-form-item>
        <el-form-item label="内存上限"><el-input v-model="editForm.memoryLimit" /></el-form-item>
        <el-form-item label="服务描述"><el-input v-model="editForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="微服务详情" size="520px">
      <div v-loading="detailLoading">
        <template v-if="detailService">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="服务编码">{{ detailService.serviceCode }}</el-descriptions-item>
            <el-descriptions-item label="服务名称">{{ detailService.serviceName }}</el-descriptions-item>
            <el-descriptions-item label="代码仓库">{{ detailService.repositoryUrl }}</el-descriptions-item>
            <el-descriptions-item label="构建分支">{{ detailService.branchName }}</el-descriptions-item>
            <el-descriptions-item label="构建类型">{{ detailService.buildType }}</el-descriptions-item>
            <el-descriptions-item label="镜像仓库">{{ detailService.imageRepository }}</el-descriptions-item>
            <el-descriptions-item label="端口 / 副本">{{ detailService.port }} / {{ detailService.replicas }}</el-descriptions-item>
            <el-descriptions-item label="资源限制">{{ detailService.cpuLimit || '-' }} / {{ detailService.memoryLimit || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="detailService.status === 'ACTIVE' ? 'success' : 'info'">{{ detailService.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="描述">{{ detailService.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDateTime(detailService.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </section>
</template>
