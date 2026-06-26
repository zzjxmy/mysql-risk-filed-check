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
