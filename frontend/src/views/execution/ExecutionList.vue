<template>
  <div class="execution-list">
    <div class="page-header">
      <h2>执行记录</h2>
    </div>

    <el-card shadow="never">
      <el-form :inline="true" class="search-form">
        <el-form-item label="任务名称">
          <el-input v-model="searchForm.taskName" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="待执行" value="PENDING" />
            <el-option label="执行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已停止" value="STOPPED" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发方式">
          <el-select v-model="searchForm.triggerType" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="手动" value="MANUAL" />
            <el-option label="定时调度" value="SCHEDULED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="taskName" label="任务名称" min-width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发方式" width="100">
          <template #default="{ row }">
            {{ row.triggerType === 'MANUAL' ? '手动' : '定时' }}
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="totalTables" label="检查表数" width="100">
          <template #default="{ row }">
            {{ row.processedTables || 0 }} / {{ row.totalTables || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="riskCount" label="风险数" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.riskCount > 0" type="danger">{{ row.riskCount }}</el-tag>
            <span v-else>{{ row.riskCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="150">
          <template #default="{ row }">
            <el-progress 
              :percentage="calculateProgress(row)" 
              :status="getProgressStatus(row.status)"
              :stroke-width="8"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button 
              v-if="row.status === 'RUNNING'" 
              type="primary" 
              link 
              @click="handleMonitor(row)"
            >
              <el-icon><VideoPlay /></el-icon>
              监控
            </el-button>
            <el-button type="primary" link @click="viewRisks(row)">
              <el-icon><Warning /></el-icon>
              风险结果
            </el-button>
            <el-button type="primary" link @click="viewLog(row)">
              <el-icon><Document /></el-icon>
              日志
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 日志对话框 -->
    <el-dialog v-model="logVisible" title="执行日志" width="800px">
      <div class="log-container">
        <pre>{{ logContent }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTaskExecutions, getExecutionLog, type Execution } from '../../api/task'

const router = useRouter()
const loading = ref(false)
const tableData = ref<Execution[]>([])
const logVisible = ref(false)
const logContent = ref('')

const searchForm = reactive({
  taskName: '',
  status: '',
  triggerType: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info',
    RUNNING: 'primary',
    SUCCESS: 'success',
    FAILED: 'danger',
    STOPPED: 'warning'
  }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待执行',
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '失败',
    STOPPED: '已停止'
  }
  return map[status] || status
}

const getProgressStatus = (status: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'exception'
  return undefined
}

const calculateProgress = (row: Execution) => {
  if (!row.totalTables || row.totalTables === 0) return 0
  return Math.round(((row.processedTables || 0) / row.totalTables) * 100)
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const fetchData = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page - 1,
      size: pagination.size
    }
    
    if (searchForm.taskName) params.taskName = searchForm.taskName
    if (searchForm.status) params.status = searchForm.status
    if (searchForm.triggerType) params.triggerType = searchForm.triggerType
    
    const res = await getTaskExecutions(undefined, params)
    if (res.code === 200) {
      tableData.value = res.data.content
      pagination.total = res.data.totalElements
    }
  } catch (error) {
    console.error('获取执行记录失败:', error)
    ElMessage.error('获取执行记录失败')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.taskName = ''
  searchForm.status = ''
  searchForm.triggerType = ''
  pagination.page = 1
  fetchData()
}

const handleMonitor = (row: Execution) => {
  router.push(`/tasks/${row.taskId}/monitor`)
}

const viewRisks = (row: Execution) => {
  router.push({
    path: '/risks',
    query: { executionId: row.id }
  })
}

const viewLog = async (row: Execution) => {
  try {
    const res = await getExecutionLog(row.id)
    if (res.code === 200) {
      logContent.value = res.data || '暂无日志'
      logVisible.value = true
    }
  } catch (error) {
    ElMessage.error('获取日志失败')
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.execution-list {
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
}

.search-form {
  margin-bottom: 20px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.log-container {
  max-height: 500px;
  overflow-y: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 15px;
  border-radius: 4px;
}

.log-container pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
}
</style>
