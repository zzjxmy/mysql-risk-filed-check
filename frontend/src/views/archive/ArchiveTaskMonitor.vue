<template>
  <div class="archive-task-monitor">
    <el-empty v-if="!execution && !loading" description="暂无归档执行记录">
      <el-button type="primary" @click="$router.push('/archive-tasks')">返回归档任务</el-button>
    </el-empty>

    <el-row v-else :gutter="20">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>归档日志</span>
              <div>
                <el-button :disabled="execution?.status !== 'RUNNING'" @click="handleStop">停止任务</el-button>
                <el-button @click="scrollToBottom">滚动到底部</el-button>
              </div>
            </div>
          </template>
          <div ref="logContainerRef" class="log-container">
            <div v-for="(log, index) in visibleLogs" :key="index" :class="['log-line', `log-${log.level.toLowerCase()}`]">
              <span class="log-time">[{{ log.timestamp }}]</span>
              <span class="log-level">[{{ log.level }}]</span>
              <span class="log-message">{{ log.message }}</span>
            </div>
            <div v-if="logs.length === 0" class="log-empty">暂无日志</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>执行状态</template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="任务名称">{{ execution?.taskName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusType">{{ statusText }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ execution?.startTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ execution?.endTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="完成步骤">{{ execution?.processedSteps || 0 }} / {{ execution?.totalSteps || 0 }}</el-descriptions-item>
            <el-descriptions-item label="跳过步骤">{{ execution?.skippedSteps || 0 }}</el-descriptions-item>
            <el-descriptions-item label="退出码">{{ execution?.exitCode ?? '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-progress :percentage="execution?.progressPercent || 0" :status="progressStatus" style="margin-top: 20px" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import SockJS from 'sockjs-client'
import Stomp from 'stompjs'
import { getArchiveExecution, getArchiveExecutionLog, getArchiveTaskExecutions, stopArchiveTask, type ArchiveExecution } from '../../api/archive'

const route = useRoute()
const taskId = Number(route.params.id)
const executionIdFromQuery = route.query.executionId ? Number(route.query.executionId) : null
const execution = ref<ArchiveExecution | null>(null)
const logs = ref<Array<{ timestamp: string; level: string; message: string }>>([])
const logContainerRef = ref<HTMLDivElement>()
const loading = ref(true)
let stompClient: any = null
let refreshInterval: any = null
const MAX_LOGS = 500
const VISIBLE_LOGS = 150

const visibleLogs = computed(() => logs.value.slice(-VISIBLE_LOGS))
const statusType = computed(() => ({ RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger', STOPPED: 'warning' }[execution.value?.status || ''] || 'info'))
const statusText = computed(() => ({ RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败', STOPPED: '已停止', PENDING: '待执行' }[execution.value?.status || ''] || '未知'))
const progressStatus = computed(() => execution.value?.status === 'SUCCESS' ? 'success' : execution.value?.status === 'FAILED' ? 'exception' : undefined)

const connectWebSocket = (executionId: number) => {
  const socket = new SockJS('/ws')
  stompClient = Stomp.over(socket)
  stompClient.debug = null
  stompClient.connect({}, () => {
    stompClient.subscribe(`/topic/archive-execution/${executionId}/log`, (message: any) => {
      const log = JSON.parse(message.body)
      logs.value.push({ timestamp: log.timestamp, level: log.level, message: log.message })
      if (logs.value.length > MAX_LOGS) logs.value = logs.value.slice(-MAX_LOGS)
      nextTick(scrollToBottom)
    })
  })
}

const parseLogContent = (content: string) => {
  logs.value = content.split('\n').filter(Boolean).slice(-MAX_LOGS).map(line => {
    const match = line.match(/^\[(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2})\]\s*\[(\w+)\]\s*(.*)$/)
    return match ? { timestamp: match[1], level: match[2], message: match[3] } : { timestamp: '', level: 'INFO', message: line }
  })
}

const loadHistoricalLogs = async (executionId: number) => {
  const res = await getArchiveExecutionLog(executionId)
  if (res.code === 200 && res.data) parseLogContent(res.data)
}

const fetchExecution = async () => {
  try {
    if (executionIdFromQuery) {
      const res = await getArchiveExecution(executionIdFromQuery)
      if (res.code === 200) execution.value = res.data
    } else {
      const res = await getArchiveTaskExecutions(taskId, { page: 0, size: 1 })
      if (res.code === 200 && res.data.content.length > 0) execution.value = res.data.content[0]
    }
    if (execution.value && logs.value.length === 0) await loadHistoricalLogs(execution.value.id)
    if (execution.value && execution.value.status === 'RUNNING' && !stompClient) connectWebSocket(execution.value.id)
  } finally {
    loading.value = false
  }
}

const handleStop = async () => {
  await stopArchiveTask(taskId)
  ElMessage.success('停止请求已发送')
  fetchExecution()
}

const scrollToBottom = () => {
  if (logContainerRef.value) logContainerRef.value.scrollTop = logContainerRef.value.scrollHeight
}

onMounted(() => {
  fetchExecution()
  refreshInterval = setInterval(() => {
    if (execution.value?.status === 'RUNNING') fetchExecution()
  }, 2000)
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
  if (stompClient) stompClient.disconnect()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.log-container {
  height: 520px;
  overflow-y: auto;
  background: #1e1e1e;
  padding: 10px;
  border-radius: 4px;
  font-family: Consolas, monospace;
  font-size: 13px;
}
.log-line {
  line-height: 1.6;
}
.log-time { color: #888; }
.log-info .log-level { color: #58a6ff; }
.log-info .log-message { color: #c9d1d9; }
.log-warn .log-level, .log-warn .log-message { color: #d29922; }
.log-error .log-level, .log-error .log-message { color: #f85149; }
.log-empty {
  text-align: center;
  color: #666;
  padding: 40px;
}
</style>
