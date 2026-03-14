import request from '../utils/request'

export interface Task {
  id?: number
  name: string
  connectionId: number
  connectionName?: string
  dbPattern?: string
  tablePattern?: string
  fullScan?: boolean
  sampleSize?: number
  maxTableRows?: number
  thresholdPct?: number
  y2038WarningYear?: number
  whitelistType?: string
  customWhitelist?: string
  cronExpression?: string
  status?: string
  alertConfigIds?: number[]
}

export interface Execution {
  id: number
  taskId: number
  taskName: string
  startTime: string
  endTime: string
  status: string
  totalTables: number
  processedTables: number
  riskCount: number
  logPath: string
  errorMessage: string
  triggerType: string
  progressPercent: number
}

export function getTasks(params?: { name?: string; status?: string; connectionId?: number; page?: number; size?: number }) {
  return request.get('/tasks', { params })
}

export function getTask(id: number) {
  return request.get(`/tasks/${id}`)
}

export function createTask(data: Task) {
  return request.post('/tasks', data)
}

export function updateTask(id: number, data: Task) {
  return request.put(`/tasks/${id}`, data)
}

export function deleteTask(id: number) {
  return request.delete(`/tasks/${id}`)
}

export function runTask(id: number) {
  return request.post(`/tasks/${id}/run`)
}

export function stopTask(id: number) {
  return request.post(`/tasks/${id}/stop`)
}

export function getTaskExecutions(taskId: number, params?: { page?: number; size?: number }) {
  return request.get(`/tasks/${taskId}/executions`, { params })
}

export function getExecution(id: number) {
  return request.get(`/executions/${id}`)
}

export function getExecutionProgress(id: number) {
  return request.get(`/executions/${id}/progress`)
}

export function getExecutionLog(id: number) {
  return request.get(`/executions/${id}/log`)
}
