import request from '../utils/request'

export interface TableStats {
  schemaName: string
  tableName: string
  tableComment?: string
  tableRows: number
  dataSizeMb: number
  indexSizeMb: number
  totalSizeMb: number
  fragmentSizeMb: number
  fragmentPercent: number
  engine?: string
}

export interface TableStatsParams {
  connectionId: number
  schema?: string
  keyword?: string
  minFragmentMb?: number
}

export function getTableStats(params: TableStatsParams) {
  return request.get('/table-stats', { params })
}

export function getTableStatsExportUrl(params: TableStatsParams) {
  const searchParams = new URLSearchParams()
  searchParams.append('connectionId', String(params.connectionId))
  if (params.schema) searchParams.append('schema', params.schema)
  if (params.keyword) searchParams.append('keyword', params.keyword)
  if (params.minFragmentMb != null) searchParams.append('minFragmentMb', String(params.minFragmentMb))
  return `/api/table-stats/export?${searchParams.toString()}`
}
