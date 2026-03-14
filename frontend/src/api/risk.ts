import request from '../utils/request'

export interface RiskResult {
  id: number
  executionId: number
  databaseName: string
  tableName: string
  columnName: string
  columnType: string
  riskType: string
  riskTypeDesc: string
  currentValue: string
  thresholdValue: string
  usagePercent: number
  detail: string
  suggestion: string
  status: string
  remark: string
  createdAt: string
}

export interface RiskStats {
  totalRisks: number
  pendingRisks: number
  ignoredRisks: number
  resolvedRisks: number
  risksByType: Record<string, number>
  riskTrend: Array<{ date: string; count: number }>
}

export function getRisks(params?: {
  executionId?: number
  databaseName?: string
  tableName?: string
  riskType?: string
  status?: string
  page?: number
  size?: number
}) {
  return request.get('/risks', { params })
}

export function getRisk(id: number) {
  return request.get(`/risks/${id}`)
}

export function getRiskStats() {
  return request.get('/risks/stats')
}

export function updateRiskStatus(id: number, status: string, remark?: string) {
  return request.put(`/risks/${id}/status`, { status, remark })
}
