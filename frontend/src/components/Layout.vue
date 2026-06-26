<template>
  <el-container class="layout-container">
    <el-aside :width="asideWidth" class="aside" :class="{ 'aside-collapsed': isAsideCollapsed }">
      <div class="logo">
        <el-icon v-if="isAsideCollapsed" class="logo-icon"><DataBoard /></el-icon>
        <span v-else>MySQL数据治理平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="defaultOpeneds"
        class="menu"
        :class="{ 'menu-collapsed': isAsideCollapsed }"
        :collapse="isAsideCollapsed"
        :collapse-transition="false"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>

        <el-sub-menu index="common-config">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>通用配置</span>
          </template>
          <el-menu-item index="/connections">
            <el-icon><Link /></el-icon>
            <span>连接管理</span>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="field-risk">
          <template #title>
            <el-icon><Warning /></el-icon>
            <span>字段风险检查</span>
          </template>
          <el-menu-item index="/tasks">
            <el-icon><List /></el-icon>
            <span>任务管理</span>
          </el-menu-item>
          <el-menu-item index="/executions">
            <el-icon><Timer /></el-icon>
            <span>执行记录</span>
          </el-menu-item>
          <el-menu-item index="/risks">
            <el-icon><Warning /></el-icon>
            <span>风险结果</span>
          </el-menu-item>
          <el-menu-item index="/whitelist">
            <el-icon><Document /></el-icon>
            <span>白名单</span>
          </el-menu-item>
          <el-menu-item index="/alerts">
            <el-icon><Bell /></el-icon>
            <span>告警配置</span>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="data-archive">
          <template #title>
            <el-icon><Box /></el-icon>
            <span>数据归档</span>
          </template>
          <el-menu-item index="/archive-tasks">
            <el-icon><Box /></el-icon>
            <span>归档任务</span>
          </el-menu-item>
          <el-menu-item index="/archive-executions">
            <el-icon><Clock /></el-icon>
            <span>归档执行</span>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="system-manage" v-if="userStore.userInfo?.role === 'ADMIN'">
          <template #title>
            <el-icon><Operation /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/audit-logs">
            <el-icon><Tickets /></el-icon>
            <span>审计日志</span>
          </el-menu-item>
          <el-menu-item index="/ldap-config">
            <el-icon><Connection /></el-icon>
            <span>LDAP配置</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-tooltip :content="isAsideCollapsed ? '展开菜单' : '收起菜单'" placement="bottom">
            <el-button class="collapse-btn" text @click="toggleAside">
              <el-icon>
                <Expand v-if="isAsideCollapsed" />
                <Fold v-else />
              </el-icon>
            </el-button>
          </el-tooltip>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentRoute.meta?.title">
              {{ currentRoute.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" icon="User" />
              <span class="username">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isAsideCollapsed = ref(false)
const asideWidth = computed(() => (isAsideCollapsed.value ? '64px' : '200px'))
const defaultOpeneds = ['common-config', 'field-risk', 'data-archive', 'system-manage']

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/tasks/')) {
    return '/tasks'
  }
  if (path.startsWith('/archive-tasks/')) {
    return '/archive-tasks'
  }
  return path
})
const currentRoute = computed(() => route)

const toggleAside = () => {
  isAsideCollapsed.value = !isAsideCollapsed.value
}

const handleCommand = (command: string) => {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.aside {
  background-color: #304156;
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.2s ease;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  background-color: #263445;
  white-space: nowrap;
}

.logo-icon {
  font-size: 22px;
}

.menu {
  height: calc(100vh - 60px);
  border-right: none;
}

.menu:not(.el-menu--collapse) {
  width: 200px;
}

.menu :deep(.el-menu-item-group__title) {
  padding: 12px 20px 6px;
  color: #8fa1b7;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
}

.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
}

.menu.menu-collapsed :deep(.el-sub-menu__title span),
.menu.menu-collapsed :deep(.el-menu-item span),
.menu.menu-collapsed :deep(.el-sub-menu__icon-arrow) {
  display: none;
}

.menu.menu-collapsed :deep(.el-menu-item),
.menu.menu-collapsed :deep(.el-sub-menu__title) {
  padding: 0 20px;
}

.header {
  background-color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-left {
  display: flex;
  align-items: center;
}

.collapse-btn {
  width: 32px;
  margin-right: 12px;
  color: #606266;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.username {
  margin: 0 8px;
}

.main {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
