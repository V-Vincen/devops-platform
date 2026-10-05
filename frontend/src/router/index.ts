import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/DashboardView.vue'),
    },
    {
      path: '/projects',
      name: 'projects',
      component: () => import('../views/ProjectListView.vue'),
    },
    {
      path: '/environments',
      name: 'environments',
      component: () => import('../views/EnvironmentListView.vue'),
    },
    {
      path: '/microservices',
      name: 'microservices',
      component: () => import('../views/MicroserviceListView.vue'),
    },
    {
      path: '/projects/:projectId/environments',
      name: 'project-environments',
      component: () => import('../views/EnvironmentListView.vue'),
    },
    {
      path: '/projects/:projectId/services',
      name: 'project-services',
      component: () => import('../views/MicroserviceListView.vue'),
    },
    {
      path: '/projects/:projectId/environments/:environmentId/services',
      name: 'environment-services',
      component: () => import('../views/MicroserviceListView.vue'),
    },
    {
      path: '/settings/users',
      name: 'user-management',
      component: () => import('../views/UserManagementView.vue'),
      meta: { requiresAdmin: true },
    },
  ],
})

/**
 * 页面级登录拦截，接口级权限由后端再次校验。
 */
router.beforeEach((to) => {
  const hasToken = Boolean(localStorage.getItem('devops-access-token'))
  if (to.name === 'login') {
    return hasToken ? '/dashboard' : true
  }
  if (!hasToken) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 前端仅负责避免误入无权限页面，服务端仍是权限校验的最终边界。
  if (to.meta.requiresAdmin && !isAdministrator()) {
    return { name: 'dashboard' }
  }
  return true
})

function isAdministrator() {
  try {
    const raw = localStorage.getItem('devops-current-user')
    return raw ? JSON.parse(raw).role === 'ADMIN' : false
  } catch {
    return false
  }
}

export default router
