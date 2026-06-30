<template>
  <div class="archive-execution-list">
    <el-card>
      <template #header>归档执行记录</template>
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
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="searchForm.startRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="resetSearch">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="taskName" label="任务名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发方式" width="100">
          <template #default="{ row }">{{ row.triggerType === 'MANUAL' ? '手动' : '定时' }}</template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="180">
          <template #default="{ row }">{{ formatDate(row.startTime) }}</template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" width="180">
          <template #default="{ row }">{{ formatDate(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="步骤" width="120">
          <template #default="{ row }">{{ row.processedSteps || 0 }} / {{ row.totalSteps || 0 }}</template>
        </el-table-column>
        <el-table-column prop="skippedSteps" label="跳过" width="80" />
        <el-table-column label="进度" width="150">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPercent || 0" :status="getProgressStatus(row.status)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push({ path: `/archive-tasks/${row.taskId}/monitor`, query: { executionId: row.id } })">监控</el-button>
            <el-button type="primary" link @click="viewLog(row)">日志</el-button>
            <el-button type="success" link @click="downloadLog(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </el-card>

    <el-dialog v-model="logVisible" title="归档日志" width="900px">
      <div class="log-container">
        <div v-for="(line, index) in parsedLogs" :key="index" :class="['log-line', `log-${line.level.toLowerCase()}`]">
          <span class="log-time">[{{ line.time }}]</span>
          <span class="log-level">[{{ line.level }}]</span>
          <span class="log-message">{{ line.message }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getArchiveExecutionLog, getArchiveExecutionLogDownloadUrl, getArchiveTaskExecutions, type ArchiveExecution, type ArchiveExecutionQuery } from '../../api/archive'

const loading = ref(false)
const tableData = ref<ArchiveExecution[]>([])
const logVisible = ref(false)
const parsedLogs = ref<Array<{ time: string; level: string; message: string }>>([])
const searchForm = reactive({
  taskName: '',
  status: '',
  triggerType: '',
  startRange: [] as string[]
})
const pagination = reactive({ page: 1, size: 20, total: 0 })

const getStatusType = (status: string) => ({ RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger', STOPPED: 'warning' }[status] || 'info')
const getStatusText = (status: string) => ({ RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败', STOPPED: '已停止', PENDING: '待执行' }[status] || status)
const getProgressStatus = (status: string) => status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'exception' : undefined
const formatDate = (dateStr?: string) => dateStr ? new Date(dateStr).toLocaleString('zh-CN') : '-'

const fetchData = async () => {
  loading.value = true
  try {
    const params: ArchiveExecutionQuery = {
      page: pagination.page - 1,
      size: pagination.size
    }
    if (searchForm.taskName) params.taskName = searchForm.taskName
    if (searchForm.status) params.status = searchForm.status
    if (searchForm.triggerType) params.triggerType = searchForm.triggerType
    if (searchForm.startRange?.length === 2) {
      params.startFrom = searchForm.startRange[0]
      params.startTo = searchForm.startRange[1]
    }

    const res = await getArchiveTaskExecutions(undefined, params)
    if (res.code === 200) {
      tableData.value = res.data.content
      pagination.total = res.data.totalElements
    }
  } catch (error) {
    ElMessage.error('获取归档执行记录失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchData()
}

const resetSearch = () => {
  searchForm.taskName = ''
  searchForm.status = ''
  searchForm.triggerType = ''
  searchForm.startRange = []
  pagination.page = 1
  fetchData()
}

const viewLog = async (row: ArchiveExecution) => {
  const res = await getArchiveExecutionLog(row.id)
  if (res.code === 200) {
    parsedLogs.value = res.data.split('\n').filter(Boolean).map((line: string) => {
      const match = line.match(/^\[(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2})\]\s*\[(\w+)\]\s*(.*)$/)
      return match ? { time: match[1], level: match[2], message: match[3] } : { time: '', level: 'INFO', message: line }
    })
    logVisible.value = true
  }
}

const downloadLog = (row: ArchiveExecution) => {
  window.open(getArchiveExecutionLogDownloadUrl(row.id), '_blank')
}

onMounted(fetchData)
</script>

<style scoped>
.search-form {
  margin-bottom: 20px;
}

.log-container {
  max-height: 560px;
  overflow-y: auto;
  background: #1e1e1e;
  padding: 10px;
  border-radius: 4px;
  font-family: Consolas, monospace;
  font-size: 13px;
}
.log-line { line-height: 1.6; }
.log-time { color: #888; }
.log-info .log-level { color: #58a6ff; }
.log-info .log-message { color: #c9d1d9; }
.log-warn .log-level, .log-warn .log-message { color: #d29922; }
.log-error .log-level, .log-error .log-message { color: #f85149; }
</style>
