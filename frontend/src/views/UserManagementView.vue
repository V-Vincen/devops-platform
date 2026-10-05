<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchProjects, type Project } from '../api/project'
import {
  createUser,
  fetchGrantedProjectIds,
  fetchUsers,
  grantProject,
  revokeProject,
  type PlatformUser,
  type UserRole,
} from '../api/user'

const users = ref<PlatformUser[]>([])
const projects = ref<Project[]>([])
const loading = ref(false)
const submitting = ref(false)
const granting = ref(false)
const createVisible = ref(false)
const selectedUserId = ref('')
const selectedProjectId = ref('')
const grantedProjectIds = ref<string[]>([])
const createFormRef = ref()

const createForm = reactive<{ username: string; displayName: string; password: string; role: UserRole }>({
  username: '',
  displayName: '',
  password: '',
  role: 'DEVELOPER',
})

const rules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 8, message: '密码至少为 8 位', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const selectedUser = computed(() => users.value.find((user) => user.id === selectedUserId.value) ?? null)
const grantedProjects = computed(() => projects.value.filter((project) => grantedProjectIds.value.includes(project.id)))
const availableProjects = computed(() => projects.value.filter((project) => !grantedProjectIds.value.includes(project.id)))

async function loadGrantedProjects() {
  if (!selectedUser.value || selectedUser.value.role !== 'DEVELOPER') {
    grantedProjectIds.value = []
    return
  }
  grantedProjectIds.value = await fetchGrantedProjectIds(selectedUser.value.id)
}

async function loadPage() {
  loading.value = true
  try {
    const [userData, projectData] = await Promise.all([
      fetchUsers(),
      fetchProjects({ current: 1, size: 100 }),
    ])
    users.value = userData
    projects.value = projectData.records
    if (!users.value.some((user) => user.id === selectedUserId.value)) {
      selectedUserId.value = users.value[0]?.id ?? ''
    }
    await loadGrantedProjects()
  } finally {
    loading.value = false
  }
}

async function handleUserChange() {
  selectedProjectId.value = ''
  await loadGrantedProjects()
}

function resetCreateForm() {
  Object.assign(createForm, { username: '', displayName: '', password: '', role: 'DEVELOPER' })
}

async function submitCreate() {
  await createFormRef.value.validate()
  submitting.value = true
  try {
    const user = await createUser(createForm)
    createVisible.value = false
    resetCreateForm()
    await loadPage()
    selectedUserId.value = user.id
    await loadGrantedProjects()
    ElMessage.success('用户已创建，可继续配置项目权限')
  } finally {
    submitting.value = false
  }
}

async function submitGrant() {
  if (!selectedUser.value || selectedUser.value.role !== 'DEVELOPER') {
    ElMessage.warning('请选择开发人员账号后再配置项目权限')
    return
  }
  if (!selectedProjectId.value) {
    ElMessage.warning('请选择需要授权的项目')
    return
  }

  granting.value = true
  try {
    await grantProject(selectedUser.value.id, selectedProjectId.value)
    selectedProjectId.value = ''
    await loadGrantedProjects()
    ElMessage.success('项目访问权限已授予')
  } finally {
    granting.value = false
  }
}

async function removeGrant(project: Project) {
  if (!selectedUser.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定解除“${selectedUser.value.displayName}”对项目“${project.name}”的访问权限吗？`,
      '解除项目授权',
      { type: 'warning', confirmButtonText: '确定解除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  granting.value = true
  try {
    await revokeProject(selectedUser.value.id, project.id)
    await loadGrantedProjects()
    ElMessage.success('项目访问权限已解除')
  } finally {
    granting.value = false
  }
}

function formatDateTime(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(loadPage)
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">ACCESS CONTROL</p>
        <h1>用户与权限</h1>
        <p class="page-description">由管理员创建内部账号，并为开发人员按项目授予访问权限。</p>
      </div>
      <el-button type="primary" @click="createVisible = true">新建用户</el-button>
    </div>

    <el-card shadow="never" class="content-card permission-card" v-loading="loading">
      <div class="context-selector permission-selector">
        <div>
          <span class="context-label">配置对象</span>
          <span class="context-description">管理员默认拥有所有项目权限；开发人员按项目单独授权。</span>
        </div>
        <el-select v-model="selectedUserId" placeholder="选择用户" @change="handleUserChange">
          <el-option v-for="user in users" :key="user.id" :label="`${user.displayName}（${user.username}）`" :value="user.id">
            <span>{{ user.displayName }}（{{ user.username }}）</span>
            <span class="option-role">{{ user.role === 'ADMIN' ? '管理员' : '开发人员' }}</span>
          </el-option>
        </el-select>
      </div>

      <template v-if="selectedUser">
        <div v-if="selectedUser.role === 'ADMIN'" class="permission-notice">
          <el-tag type="danger">管理员</el-tag>
          <span>该账号可访问平台全部项目，无需配置逐项目授权。</span>
        </div>
        <template v-else>
          <div class="grant-form">
            <el-select v-model="selectedProjectId" clearable placeholder="选择需要授权的项目">
              <el-option v-for="project in availableProjects" :key="project.id" :label="`${project.name}（${project.code}）`" :value="project.id" />
            </el-select>
            <el-button type="primary" :loading="granting" :disabled="availableProjects.length === 0" @click="submitGrant">授予权限</el-button>
          </div>

          <el-table :data="grantedProjects" row-key="id" empty-text="当前开发人员尚未获得项目访问权限">
            <el-table-column prop="code" label="项目编码" min-width="180" />
            <el-table-column prop="name" label="项目名称" min-width="180" />
            <el-table-column prop="description" label="项目描述" min-width="260" show-overflow-tooltip />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" :loading="granting" @click="removeGrant(row)">解除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </el-card>

    <el-card shadow="never" class="content-card user-list-card">
      <template #header>
        <span class="card-title">平台用户</span>
      </template>
      <el-table :data="users" row-key="id">
        <el-table-column prop="username" label="登录账号" min-width="160" />
        <el-table-column prop="displayName" label="显示名称" min-width="160" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'">
              {{ row.role === 'ADMIN' ? '管理员' : '开发人员' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="190">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="新建平台用户" width="520px" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="rules" label-width="96px">
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="createForm.username" autocomplete="off" placeholder="例如 zhangsan" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="createForm.displayName" placeholder="例如 张三" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="createForm.role">
            <el-radio value="DEVELOPER">开发人员</el-radio>
            <el-radio value="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </section>
</template>
