<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  Delete,
  EditPen,
  FolderOpened,
  Monitor,
  Plus,
  RefreshLeft,
  Search,
  SwitchButton,
  View,
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteProjects, type Project } from '../api/project'
import { useProjectStore } from '../stores/project'

const store = useProjectStore()
const router = useRouter()

const createVisible = ref(false)
const editVisible = ref(false)
const detailVisible = ref(false)
const submitting = ref(false)
const detailLoading = ref(false)
const batchDeleting = ref(false)
const actionLoadingId = ref<string | null>(null)
const selectedRows = ref<Project[]>([])
const createFormRef = ref()
const editFormRef = ref()
const detailProject = ref<Project | null>(null)

const createForm = reactive({
  code: '',
  name: '',
  description: '',
})

const editForm = reactive({
  id: '',
  code: '',
  name: '',
  description: '',
})

const rules = {
  code: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
}

async function search() {
  store.current = 1
  await store.load()
}

async function resetSearch() {
  store.keyword = ''
  store.status = ''
  await search()
}

function handleSelectionChange(rows: Project[]) {
  selectedRows.value = rows
}

function resetCreateForm() {
  Object.assign(createForm, { code: '', name: '', description: '' })
}

function resetEditForm() {
  Object.assign(editForm, { id: '', code: '', name: '', description: '' })
}

async function submitCreate() {
  await createFormRef.value.validate()
  submitting.value = true
  try {
    await store.add(createForm)
    createVisible.value = false
    resetCreateForm()
    ElMessage.success('项目创建成功')
  } finally {
    submitting.value = false
  }
}

function openEdit(row: Project) {
  Object.assign(editForm, {
    id: row.id,
    code: row.code,
    name: row.name,
    description: row.description ?? '',
  })
  editVisible.value = true
}

function openSelectedEdit() {
  if (selectedRows.value.length === 1) {
    openEdit(selectedRows.value[0])
  }
}

function openEnvironments(row: Project) {
  // 环境管理保留全局分组视图；携带项目上下文后只展示当前项目的环境。
  router.push({ name: 'environments', query: { projectId: row.id } })
}

function openMicroservices(row: Project) {
  router.push(`/projects/${row.id}/services`)
}

async function submitEdit() {
  await editFormRef.value.validate()
  submitting.value = true
  try {
    await store.update(editForm.id, {
      name: editForm.name,
      description: editForm.description,
    })
    editVisible.value = false
    resetEditForm()
    ElMessage.success('项目修改成功')
  } finally {
    submitting.value = false
  }
}

async function openDetail(row: Project) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailProject.value = await store.getOne(row.id)
  } finally {
    detailLoading.value = false
  }
}

async function toggleStatus(row: Project) {
  const targetStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionText = targetStatus === 'DISABLED' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${actionText}项目“${row.name}”吗？`,
      `${actionText}项目`,
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  actionLoadingId.value = row.id
  try {
    await store.changeStatus(row.id, targetStatus)
    ElMessage.success(`项目已${actionText}`)
  } finally {
    actionLoadingId.value = null
  }
}

/**
 * 使用服务端批量接口删除项目。服务端会在一个事务中校验所有状态和下级资源，
 * 不允许前端逐条删除导致部分项目被删除。
 */
async function removeProjects(rows: Project[] = selectedRows.value) {
  if (rows.length === 0) {
    ElMessage.warning('请先选择需要删除的项目')
    return
  }
  if (rows.some((row) => row.status === 'ACTIVE')) {
    ElMessage.warning('选中项目包含启用状态数据，请先全部停用')
    return
  }

  try {
    await ElMessageBox.confirm(
      `删除后选中的 ${rows.length} 个项目将从默认列表隐藏，确定继续吗？`,
      '批量删除项目',
      { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  batchDeleting.value = true
  try {
    await deleteProjects(rows.map((row) => row.id))
    selectedRows.value = []
    await store.load()
    ElMessage.success(`已删除 ${rows.length} 个项目`)
  } finally {
    batchDeleting.value = false
  }
}

function formatDateTime(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(() => store.load())
</script>

<template>
  <section class="page-section">
    <div class="page-heading compact-page-heading">
      <div>
        <p class="eyebrow">PROJECTS</p>
        <div class="page-title-row">
          <h1>项目管理</h1>
          <span class="page-title-divider" />
          <p class="page-description">维护业务项目，并为后续服务、环境和流水线建立统一入口。</p>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-layout">
        <div class="filter-fields">
          <label class="filter-field">
            <span>项目关键字</span>
            <el-input v-model="store.keyword" clearable placeholder="项目编码或名称" @keyup.enter="search" />
          </label>
          <label class="filter-field">
            <span>项目状态</span>
            <el-select v-model="store.status" clearable placeholder="全部状态">
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
          <el-button
            size="small"
            class="action-button action-edit"
            :icon="EditPen"
            :disabled="selectedRows.length !== 1"
            @click="openSelectedEdit"
          >
            修改
          </el-button>
          <el-button
            size="small"
            class="action-button action-delete"
            :icon="Delete"
            :loading="batchDeleting"
            :disabled="selectedRows.length === 0"
            @click="removeProjects()"
          >
            删除
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="store.loading"
        :data="store.records"
        row-key="id"
        border
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" fixed="left" width="52" :resizable="false" />
        <el-table-column prop="code" label="项目编码" min-width="180" />
        <el-table-column prop="name" label="项目名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="409" :resizable="false" class-name="operation-column">
          <template #default="{ row }">
            <el-button size="small" class="table-action-button action-detail" :icon="View" @click="openDetail(row)">详情</el-button>
            <el-button size="small" class="table-action-button action-edit" :icon="EditPen" @click="openEdit(row)">修改</el-button>
            <el-button size="small" class="table-action-button action-primary" :icon="FolderOpened" @click="openEnvironments(row)">环境</el-button>
            <el-button size="small" class="table-action-button action-primary" :icon="Monitor" @click="openMicroservices(row)">微服务</el-button>
            <el-button
              size="small"
              class="table-action-button"
              :class="row.status === 'ACTIVE' ? 'action-warning' : 'action-success'"
              :icon="SwitchButton"
              :loading="actionLoadingId === row.id"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
            <el-button
              size="small"
              class="table-action-button action-delete"
              :icon="Delete"
              :disabled="row.status === 'ACTIVE'"
              :loading="batchDeleting"
              @click="removeProjects([row])"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="store.current"
          v-model:page-size="store.size"
          background
          layout="total, sizes, prev, pager, next"
          :total="store.total"
          @current-change="store.load"
          @size-change="store.load"
        />
      </div>
    </el-card>

    <el-dialog v-model="createVisible" title="新建项目" width="520px" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="rules" label-width="88px">
        <el-form-item label="项目编码" prop="code">
          <el-input v-model="createForm.code" placeholder="例如 demo-project" />
        </el-form-item>
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="createForm.name" placeholder="例如 演示项目" />
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑项目" width="520px" @closed="resetEditForm">
      <el-form ref="editFormRef" :model="editForm" :rules="rules" label-width="88px">
        <el-form-item label="项目编码">
          <el-input v-model="editForm.code" disabled />
        </el-form-item>
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="editForm.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input v-model="editForm.description" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="项目详情" size="480px">
      <div v-loading="detailLoading" class="project-detail-panel">
        <template v-if="detailProject">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="项目编码">{{ detailProject.code }}</el-descriptions-item>
            <el-descriptions-item label="项目名称">{{ detailProject.name }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="detailProject.status === 'ACTIVE' ? 'success' : 'info'">
                {{ detailProject.status === 'ACTIVE' ? '启用' : '停用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="项目描述">{{ detailProject.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(detailProject.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDateTime(detailProject.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </section>
</template>
