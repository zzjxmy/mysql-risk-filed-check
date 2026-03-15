<template>
  <div class="risk-list">
    <el-card>
      <template #header>
        <span>风险结果</span>
      </template>
      <el-form :inline="true" class="search-form">
        <el-form-item label="执行记录">
          <el-select 
            v-model="searchForm.executionId" 
            placeholder="选择执行记录" 
            clearable 
            filterable
            style="width: 200px"
            @change="fetchData"
          >
            <el-option 
              v-for="item in executionList" 
              :key="item.id" 
              :label="formatExecutionLabel(item)" 
              :value="item.id" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据库">
          <el-input v-model="searchForm.databaseName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="风险类型">
          <el-select v-model="searchForm.riskType" placeholder="全部" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="整型溢出" value="INT_OVERFLOW" />
            <el-option label="小数溢出" value="DECIMAL_OVERFLOW" />
            <el-option label="Y2038问题" value="Y2038" />
            <el-option label="字符串截断" value="STRING_TRUNCATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="已忽略" value="IGNORED" />
            <el-option label="已解决" value="RESOLVED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleExport" :loading="exportLoading">导出Excel</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="databaseName" label="数据库" width="120" />
        <el-table-column prop="tableName" label="表名" width="150" />
        <el-table-column prop="columnName" label="字段名" width="120" />
        <el-table-column prop="columnType" label="字段类型" width="120" />
        <el-table-column prop="riskTypeDesc" label="风险类型" width="100" />
        <el-table-column prop="usagePercent" label="使用率" width="80">
          <template #default="{ row }">
            {{ row.usagePercent ? row.usagePercent + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="suggestion" label="建议" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-dropdown @command="(cmd: string) => handleStatusChange(row, cmd)">
              <el-button link type="primary">处理</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="RESOLVED">标记已解决</el-dropdown-item>
                  <el-dropdown-item command="IGNORED">标记忽略</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @change="fetchData"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="风险详情" width="600px">
      <el-descriptions :column="1" border v-if="currentRisk">
        <el-descriptions-item label="数据库">{{ currentRisk.databaseName }}</el-descriptions-item>
        <el-descriptions-item label="表名">{{ currentRisk.tableName }}</el-descriptions-item>
        <el-descriptions-item label="字段名">{{ currentRisk.columnName }}</el-descriptions-item>
        <el-descriptions-item label="字段类型">{{ currentRisk.columnType }}</el-descriptions-item>
        <el-descriptions-item label="风险类型">{{ currentRisk.riskTypeDesc }}</el-descriptions-item>
        <el-descriptions-item label="当前值">{{ currentRisk.currentValue }}</el-descriptions-item>
        <el-descriptions-item label="阈值">{{ currentRisk.thresholdValue }}</el-descriptions-item>
        <el-descriptions-item label="使用率">{{ currentRisk.usagePercent }}%</el-descriptions-item>
        <el-descriptions-item label="详情">{{ currentRisk.detail }}</el-descriptions-item>
        <el-descriptions-item label="建议">{{ currentRisk.suggestion }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRisks, updateRiskStatus, type RiskResult } from '../../api/risk'
import { getTaskExecutions, type Execution } from '../../api/task'

const route = useRoute()
const loading = ref(false)
const exportLoading = ref(false)
const tableData = ref<RiskResult[]>([])
const executionList = ref<Execution[]>([])
const detailVisible = ref(false)
const currentRisk = ref<RiskResult | null>(null)

const searchForm = reactive({
  executionId: route.query.executionId ? Number(route.query.executionId) : undefined,
  databaseName: '',
  riskType: '',
  status: ''
})
const pagination = reactive({ page: 1, size: 20, total: 0 })

const formatExecutionLabel = (execution: Execution) => {
  const date = execution.startTime ? new Date(execution.startTime).toLocaleString('zh-CN') : '未知时间'
  const status = execution.status === 'SUCCESS' ? '成功' : 
                 execution.status === 'FAILED' ? '失败' : 
                 execution.status === 'RUNNING' ? '执行中' : execution.status
  return `#${execution.id} - ${date} [${status}]`
}

const resetSearch = () => {
  searchForm.executionId = undefined
  searchForm.databaseName = ''
  searchForm.riskType = ''
  searchForm.status = ''
  pagination.page = 1
  fetchData()
}

const fetchExecutions = async () => {
  try {
    const res = await getTaskExecutions(undefined, { page: 0, size: 100 })
    if (res.code === 200) {
      executionList.value = res.data.content
    }
  } catch (error) {
    console.error('获取执行记录失败:', error)
  }
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = { PENDING: 'warning', IGNORED: 'info', RESOLVED: 'success' }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = { PENDING: '待处理', IGNORED: '已忽略', RESOLVED: '已解决' }
  return map[status] || status
}

const handleExport = async () => {
  exportLoading.value = true
  try {
    const params = new URLSearchParams()
    if (searchForm.executionId) params.append('executionId', String(searchForm.executionId))
    if (searchForm.databaseName) params.append('databaseName', searchForm.databaseName)
    if (searchForm.riskType) params.append('riskType', searchForm.riskType)
    if (searchForm.status) params.append('status', searchForm.status)
    
    const response = await fetch(`/api/risks/export?${params.toString()}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })
    
    if (!response.ok) {
      throw new Error('导出失败')
    }
    
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    
    // Get filename from Content-Disposition header
    const contentDisposition = response.headers.get('Content-Disposition')
    let filename = `风险结果_${new Date().toISOString().slice(0, 19).replace(/[:-]/g, '')}.xlsx`
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename\*?=['"]?(?:UTF-\d['"]*)?([^;\r\n"']+)/i)
      if (filenameMatch && filenameMatch[1]) {
        filename = decodeURIComponent(filenameMatch[1])
      }
    }
    
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  } finally {
    exportLoading.value = false
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getRisks({ ...searchForm, page: pagination.page - 1, size: pagination.size })
    if (res.code === 200) {
      tableData.value = res.data.content
      pagination.total = res.data.totalElements
    }
  } finally {
    loading.value = false
  }
}

const showDetail = (row: RiskResult) => {
  currentRisk.value = row
  detailVisible.value = true
}

const handleStatusChange = async (row: RiskResult, status: string) => {
  try {
    await updateRiskStatus(row.id, status)
    ElMessage.success('更新成功')
    fetchData()
  } catch (error: any) {
    ElMessage.error(error.message || '更新失败')
  }
}

onMounted(() => { 
  fetchExecutions()
  fetchData() 
})
</script>

<style scoped>
.search-form { margin-bottom: 20px; }
</style>
