import request from '../utils/request'

export interface ArchiveVariable {
  id?: number
  name: string
  querySql: string
  connectionId?: number
  connectionName?: string
  sortOrder?: number
  enabled?: boolean
}

export interface ArchiveStep {
  id?: number
  name: string
  stepMode?: string
  sourceDatabase: string
  sourceTable: string
  indexName?: string
  destDatabase?: string
  destTable?: string
  whereTemplate: string
  deleteSource?: boolean
  charset?: string
  limitSize?: number
  progressSize?: number
  bulkInsert?: boolean
  commitEach?: boolean
  extraOptions?: string
  sortOrder?: number
  enabled?: boolean
}

export interface ArchiveBatchConfig {
  id?: number
  queryConnectionId?: number
  queryConnectionName?: string
  targetConnectionId?: number
  targetConnectionName?: string
  connectionId?: number
  connectionName?: string
  batchQuery: string
  targetDatabase: string
  targetTable: string
  truncateSql: string
  loadSql: string
  batchSize?: number
  maxRounds?: number
  enabled?: boolean
}

export interface ArchiveTask {
  id?: number
  name: string
  taskMode?: string
  sourceConnectionId: number
  sourceConnectionName?: string
  destConnectionId: number
  destConnectionName?: string
  cronExpression?: string
  status?: string
  remark?: string
  alertConfigIds?: number[]
  variables: ArchiveVariable[]
  steps: ArchiveStep[]
  batchConfig?: ArchiveBatchConfig | null
}

export interface ArchiveExecution {
  id: number
  taskId: number
  taskName: string
  startTime: string
  endTime: string
  status: string
  totalSteps: number
  processedSteps: number
  skippedSteps: number
  exitCode?: number
  variableSnapshot?: string
  logPath: string
  errorMessage?: string
  triggerType: string
  progressPercent: number
}

export function getArchiveTasks(params?: { name?: string; status?: string; page?: number; size?: number }) {
  return request.get('/archive-tasks', { params })
}

export function getArchiveTask(id: number) {
  return request.get(`/archive-tasks/${id}`)
}

export function createArchiveTask(data: ArchiveTask) {
  return request.post('/archive-tasks', data)
}

export function updateArchiveTask(id: number, data: ArchiveTask) {
  return request.put(`/archive-tasks/${id}`, data)
}

export function deleteArchiveTask(id: number) {
  return request.delete(`/archive-tasks/${id}`)
}

export function runArchiveTask(id: number) {
  return request.post(`/archive-tasks/${id}/run`)
}

export function stopArchiveTask(id: number) {
  return request.post(`/archive-tasks/${id}/stop`)
}

export function getArchiveTaskExecutions(taskId?: number, params?: { page?: number; size?: number }) {
  if (taskId) {
    return request.get(`/archive-tasks/${taskId}/executions`, { params })
  }
  return request.get('/archive-executions', { params })
}

export function getArchiveExecution(id: number) {
  return request.get(`/archive-executions/${id}`)
}

export function getArchiveExecutionLog(id: number) {
  return request.get(`/archive-executions/${id}/log`)
}

export function getArchiveExecutionLogDownloadUrl(id: number) {
  return `/api/archive-executions/${id}/log/download`
}
