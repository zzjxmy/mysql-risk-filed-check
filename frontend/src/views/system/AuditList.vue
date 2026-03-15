<template>
  <div class="audit-list">
    <div class="page-header">
      <h2>审计日志</h2>
    </div>

    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="searchForm.action" placeholder="全部" clearable>
            <el-option
              v-for="action in actionTypes"
              :key="action"
              :label="getActionLabel(action)"
              :value="action"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchLogs">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="logList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column prop="action" label="操作类型" width="150">
          <template #default="{ row }">
            <el-tag :type="getActionTagType(row.action)" size="small">
              {{ getActionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="目标类型" width="100">
          <template #default="{ row }">
            {{ row.targetType || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="targetName" label="目标名称" min-width="150">
          <template #default="{ row }">
            {{ row.targetName || row.targetId || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="200">
          <template #default="{ row }">
            <el-tooltip v-if="row.detail && row.detail.length > 50" :content="row.detail" placement="top">
              <span>{{ row.detail.substring(0, 50) }}...</span>
            </el-tooltip>
            <span v-else>{{ row.detail || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP地址" width="140" />
        <el-table-column prop="success" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDetail(row)">
              <el-icon><View /></el-icon>
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[20, 50, 100, 200]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchLogs"
          @current-change="fetchLogs"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ currentLog.username }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">
          <el-tag :type="getActionTagType(currentLog.action)">
            {{ getActionLabel(currentLog.action) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="目标类型">{{ currentLog.targetType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标ID">{{ currentLog.targetId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标名称">{{ currentLog.targetName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详情">{{ currentLog.detail || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ currentLog.ipAddress }}</el-descriptions-item>
        <el-descriptions-item label="User Agent">
          <span class="user-agent">{{ currentLog.userAgent || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentLog.success ? 'success' : 'danger'">
            {{ currentLog.success ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDate(currentLog.createdAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, View } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface AuditLog {
  id: number
  userId?: number
  username: string
  action: string
  targetType?: string
  targetId?: number
  targetName?: string
  detail?: string
  ipAddress?: string
  userAgent?: string
  success: boolean
  createdAt: string
}

const loading = ref(false)
const logList = ref<AuditLog[]>([])
const detailVisible = ref(false)
const currentLog = ref<AuditLog>({} as AuditLog)

const actionTypes = ref<string[]>([])

const searchForm = reactive({
  username: '',
  action: undefined as string | undefined,
  dateRange: null as string[] | null
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const actionLabels: Record<string, string> = {
  'LOGIN': '登录',
  'LOGOUT': '登出',
  'CREATE_CONNECTION': '创建连接',
  'UPDATE_CONNECTION': '更新连接',
  'DELETE_CONNECTION': '删除连接',
  'TEST_CONNECTION': '测试连接',
  'CREATE_TASK': '创建任务',
  'UPDATE_TASK': '更新任务',
  'DELETE_TASK': '删除任务',
  'EXECUTE_TASK': '执行任务',
  'STOP_TASK': '停止任务',
  'CREATE_WHITELIST': '创建白名单',
  'UPDATE_WHITELIST': '更新白名单',
  'DELETE_WHITELIST': '删除白名单',
  'CREATE_ALERT': '创建告警',
  'UPDATE_ALERT': '更新告警',
  'DELETE_ALERT': '删除告警',
  'TEST_ALERT': '测试告警',
  'CREATE_USER': '创建用户',
  'UPDATE_USER': '更新用户',
  'DELETE_USER': '删除用户',
  'RESET_PASSWORD': '重置密码',
  'UPDATE_RISK_STATUS': '更新风险状态'
}

const fetchLogs = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page - 1,
      size: pagination.size
    }
    if (searchForm.username) params.username = searchForm.username
    if (searchForm.action) params.action = searchForm.action
    if (searchForm.dateRange && searchForm.dateRange.length === 2) {
      params.startTime = searchForm.dateRange[0]
      params.endTime = searchForm.dateRange[1]
    }
    
    const res = await request.get('/audit-logs', { params })
    logList.value = res.data.content
    pagination.total = res.data.totalElements
  } catch (error) {
    console.error('获取审计日志失败:', error)
  } finally {
    loading.value = false
  }
}

const fetchActionTypes = async () => {
  try {
    const res = await request.get('/audit-logs/actions')
    actionTypes.value = res.data
  } catch (error) {
    actionTypes.value = Object.keys(actionLabels)
  }
}

const resetSearch = () => {
  searchForm.username = ''
  searchForm.action = undefined
  searchForm.dateRange = null
  pagination.page = 1
  fetchLogs()
}

const showDetail = (log: AuditLog) => {
  currentLog.value = log
  detailVisible.value = true
}

const getActionLabel = (action: string) => {
  return actionLabels[action] || action
}

const getActionTagType = (action: string) => {
  if (action?.startsWith('DELETE')) return 'danger'
  if (action?.startsWith('CREATE')) return 'success'
  if (action?.startsWith('UPDATE')) return 'warning'
  if (action === 'LOGIN') return 'primary'
  return 'info'
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchLogs()
  fetchActionTypes()
})
</script>

<style scoped>
.audit-list {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
}

.search-card {
  margin-bottom: 20px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.user-agent {
  word-break: break-all;
  font-size: 12px;
  color: #909399;
}
</style>
