import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('../components/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/dashboard'
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'connections',
        name: 'Connections',
        component: () => import('../views/connection/ConnectionList.vue'),
        meta: { title: '连接管理' }
      },
      {
        path: 'tasks',
        name: 'Tasks',
        component: () => import('../views/task/TaskList.vue'),
        meta: { title: '任务管理' }
      },
      {
        path: 'tasks/create',
        name: 'CreateTask',
        component: () => import('../views/task/TaskForm.vue'),
        meta: { title: '创建任务' }
      },
      {
        path: 'tasks/:id/edit',
        name: 'EditTask',
        component: () => import('../views/task/TaskForm.vue'),
        meta: { title: '编辑任务' }
      },
      {
        path: 'tasks/:id/monitor',
        name: 'TaskMonitor',
        component: () => import('../views/task/TaskMonitor.vue'),
        meta: { title: '任务监控' }
      },
      {
        path: 'risks',
        name: 'Risks',
        component: () => import('../views/risk/RiskList.vue'),
        meta: { title: '风险结果' }
      },
      {
        path: 'whitelist',
        name: 'Whitelist',
        component: () => import('../views/whitelist/WhitelistList.vue'),
        meta: { title: '白名单管理' }
      },
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('../views/alert/AlertList.vue'),
        meta: { title: '告警配置' }
      },
      {
        path: 'executions',
        name: 'Executions',
        component: () => import('../views/execution/ExecutionList.vue'),
        meta: { title: '执行记录' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/system/UserList.vue'),
        meta: { title: '用户管理', roles: ['ADMIN'] }
      },
      {
        path: 'audit-logs',
        name: 'AuditLogs',
        component: () => import('../views/system/AuditList.vue'),
        meta: { title: '审计日志', roles: ['ADMIN'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  
  if (to.meta.requiresAuth !== false && !userStore.isLoggedIn) {
    next('/login')
  } else if (to.path === '/login' && userStore.isLoggedIn) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
