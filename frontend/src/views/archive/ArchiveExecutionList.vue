<template>
  <div class="archive-execution-list">
    <el-card>
      <template #header>归档执行记录</template>
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
        layout="total, sizes, prev, pager, next"
        @change="fetchData"
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
import { getArchiveExecutionLog, getArchiveExecutionLogDownloadUrl, getArchiveTaskExecutions, type ArchiveExecution } from '../../api/archive'

const loading = ref(false)
const tableData = ref<ArchiveExecution[]>([])
const logVisible = ref(false)
const parsedLogs = ref<Array<{ time: string; level: string; message: string }>>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })

const getStatusType = (status: string) => ({ RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger', STOPPED: 'warning' }[status] || 'info')
const getStatusText = (status: string) => ({ RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败', STOPPED: '已停止', PENDING: '待执行' }[status] || status)
const getProgressStatus = (status: string) => status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'exception' : undefined
const formatDate = (dateStr?: string) => dateStr ? new Date(dateStr).toLocaleString('zh-CN') : '-'

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getArchiveTaskExecutions(undefined, { page: pagination.page - 1, size: pagination.size })
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
