<template>
  <div class="task-monitor">
    <!-- 空状态提示 -->
    <el-empty v-if="!execution && !loading" description="暂无执行记录">
      <el-button type="primary" @click="$router.push('/tasks')">返回任务列表</el-button>
    </el-empty>
    
    <el-row v-else :gutter="20">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>实时日志</span>
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
            <div v-if="logs.length > MAX_LOGS" class="log-truncated">... 仅显示最近 {{ MAX_LOGS }} 条日志 ...</div>
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
            <el-descriptions-item label="检查表数">{{ execution?.processedTables || 0 }} / {{ execution?.totalTables || 0 }}</el-descriptions-item>
            <el-descriptions-item label="发现风险">{{ execution?.riskCount || 0 }}</el-descriptions-item>
          </el-descriptions>
          <el-progress :percentage="execution?.progressPercent || 0" :status="progressStatus" style="margin-top: 20px;" />
        </el-card>
        <el-card style="margin-top: 20px;">
          <template #header>快捷操作</template>
          <el-button type="primary" style="width: 100%;" :disabled="!execution" @click="$router.push('/risks?executionId=' + execution?.id)">查看风险结果</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import SockJS from 'sockjs-client'
import Stomp from 'stompjs'
import { getTaskExecutions, stopTask, getExecutionLog, getExecution, type Execution } from '../../api/task'

const route = useRoute()
const taskId = Number(route.params.id)
const executionIdFromQuery = route.query.executionId ? Number(route.query.executionId) : null
const execution = ref<Execution | null>(null)
const logs = ref<Array<{ timestamp: string; level: string; message: string }>>([])
const logContainerRef = ref<HTMLDivElement>()
const loading = ref(true)
let stompClient: any = null
const MAX_LOGS = 500 // Maximum logs to keep in memory
const VISIBLE_LOGS = 100 // Number of logs to render at once for performance

const statusType = computed(() => {
  const statusMap: Record<string, string> = {
    RUNNING: 'primary',
    SUCCESS: 'success',
    FAILED: 'danger',
    STOPPED: 'warning',
    PENDING: 'info'
  }
  return statusMap[execution.value?.status || ''] || 'info'
})

const statusText = computed(() => {
  const textMap: Record<string, string> = {
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '失败',
    STOPPED: '已停止',
    PENDING: '待执行'
  }
  return textMap[execution.value?.status || ''] || '未知'
})

const progressStatus = computed(() => {
  if (execution.value?.status === 'SUCCESS') return 'success'
  if (execution.value?.status === 'FAILED') return 'exception'
  return undefined
})

// Only render last VISIBLE_LOGS for performance
const visibleLogs = computed(() => {
  if (logs.value.length <= VISIBLE_LOGS) return logs.value
  return logs.value.slice(-VISIBLE_LOGS)
})

const connectWebSocket = (executionId: number) => {
  // Use SockJS with the correct path
  const socket = new SockJS('http://localhost:8080/ws')
  stompClient = Stomp.over(socket)
  stompClient.debug = null // Disable debug logs
  
  stompClient.connect({}, () => {
    console.log('WebSocket connected')
    stompClient.subscribe(`/topic/execution/${executionId}/log`, (message: any) => {
      const log = JSON.parse(message.body)
      logs.value.push({
        timestamp: log.timestamp,
        level: log.level,
        message: log.message
      })
      // Limit logs to prevent memory issues
      if (logs.value.length > MAX_LOGS) {
        logs.value = logs.value.slice(-MAX_LOGS)
      }
      nextTick(() => scrollToBottom())
    })
  }, (error: any) => {
    console.error('WebSocket connection error:', error)
  })
}

const scrollToBottom = () => {
  if (logContainerRef.value) {
    logContainerRef.value.scrollTop = logContainerRef.value.scrollHeight
  }
}

const handleStop = async () => {
  try {
    await stopTask(taskId)
    ElMessage.success('任务已停止')
  } catch (error: any) {
    ElMessage.error(error.message || '停止失败')
  }
}

const fetchExecution = async () => {
  try {
    // If execution ID is provided in URL, use it directly
    if (executionIdFromQuery) {
      const res = await getExecution(executionIdFromQuery)
      if (res.code === 200) {
        execution.value = res.data
        console.log('Execution fetched by ID:', execution.value)
        
        // Load historical logs first
        if (logs.value.length === 0) {
          await loadHistoricalLogs(execution.value.id)
        }
        
        // Connect WebSocket for real-time logs if task is running
        if ((execution.value.status === 'RUNNING' || execution.value.status === 'PENDING') && !stompClient) {
          connectWebSocket(execution.value.id)
        }
      }
    } else {
      // Otherwise, get the latest execution for this task
      const res = await getTaskExecutions(taskId, { page: 0, size: 1 })
      if (res.code === 200 && res.data.content.length > 0) {
        execution.value = res.data.content[0]
        console.log('Execution fetched:', execution.value)
        
        // Load historical logs first
        if (logs.value.length === 0) {
          await loadHistoricalLogs(execution.value.id)
        }
        
        // Connect WebSocket for real-time logs if task is running
        if ((execution.value.status === 'RUNNING' || execution.value.status === 'PENDING') && !stompClient) {
          connectWebSocket(execution.value.id)
        }
      } else {
        console.log('No execution found for task', taskId)
      }
    }
  } catch (error) {
    console.error('Failed to fetch execution:', error)
  } finally {
    loading.value = false
  }
}

const loadHistoricalLogs = async (executionId: number) => {
  try {
    const res = await getExecutionLog(executionId)
    if (res.code === 200 && res.data) {
      // Parse log file content
      // Format: [2026-03-15 20:04:35] [INFO] message
      const lines = res.data.split('\n').filter((line: string) => line.trim())
      // Only load last MAX_LOGS lines to prevent performance issues
      const linesToLoad = lines.length > MAX_LOGS ? lines.slice(-MAX_LOGS) : lines
      linesToLoad.forEach((line: string) => {
        const match = line.match(/^\[(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2})\]\s*\[(\w+)\]\s*(.*)$/)
        if (match) {
          logs.value.push({
            timestamp: match[1],
            level: match[2],
            message: match[3]
          })
        }
      })
      nextTick(() => scrollToBottom())
    }
  } catch (error) {
    console.error('Failed to load historical logs:', error)
  }
}

let refreshInterval: any = null

const startRefresh = () => {
  refreshInterval = setInterval(() => {
    if (execution.value?.status === 'RUNNING') {
      fetchExecution()
    } else {
      stopRefresh()
    }
  }, 2000)
}

const stopRefresh = () => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
}

onMounted(() => {
  fetchExecution()
  startRefresh()
})

onUnmounted(() => {
  stopRefresh()
  if (stompClient) {
    stompClient.disconnect()
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.log-container {
  height: 500px;
  overflow-y: auto;
  background: #1e1e1e;
  padding: 10px;
  border-radius: 4px;
  font-family: 'Consolas', monospace;
  font-size: 13px;
}
.log-line {
  line-height: 1.6;
}
.log-time {
  color: #888;
}
.log-info .log-level { color: #58a6ff; }
.log-info .log-message { color: #c9d1d9; }
.log-warn .log-level { color: #d29922; }
.log-warn .log-message { color: #d29922; }
.log-error .log-level { color: #f85149; }
.log-error .log-message { color: #f85149; }

.log-truncated {
  text-align: center;
  color: #888;
  padding: 10px;
  font-style: italic;
  border-top: 1px dashed #444;
  margin-top: 10px;
}

.log-empty {
  text-align: center;
  color: #666;
  padding: 40px;
}
</style>
