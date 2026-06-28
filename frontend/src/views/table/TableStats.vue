<template>
  <div class="table-stats">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>表空间分析</span>
          <div class="header-actions">
            <el-button :disabled="!searchForm.connectionId" :loading="exportLoading" @click="handleExport">
              <el-icon><Download /></el-icon>
              导出Excel
            </el-button>
            <el-button type="primary" :loading="loading" @click="fetchStats">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="数据库连接" required>
          <el-select
            v-model="searchForm.connectionId"
            placeholder="请选择连接"
            filterable
            style="width: 220px"
            @change="fetchStats"
          >
            <el-option
              v-for="item in connections"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库名">
          <el-input v-model="searchForm.schema" placeholder="精确库名" clearable />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="库名/表名" clearable />
        </el-form-item>
        <el-form-item label="最小碎片(M)">
          <el-input-number
            v-model="searchForm.minFragmentMb"
            :min="0"
            :precision="2"
            :step="10"
            controls-position="right"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!searchForm.connectionId" @click="fetchStats">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="summary">
        <el-statistic title="表数量" :value="summary.tableCount" />
        <el-statistic title="总数据(M)" :value="summary.dataSizeMb" :precision="2" />
        <el-statistic title="总索引(M)" :value="summary.indexSizeMb" :precision="2" />
        <el-statistic title="总碎片(M)" :value="summary.fragmentSizeMb" :precision="2" />
      </div>

      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="schemaName" label="库" min-width="130" show-overflow-tooltip />
        <el-table-column prop="tableName" label="表" min-width="180" show-overflow-tooltip />
        <el-table-column prop="tableComment" label="表备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="tableRows" label="数据量" width="120" sortable align="right">
          <template #default="{ row }">{{ formatInteger(row.tableRows) }}</template>
        </el-table-column>
        <el-table-column prop="dataSizeMb" label="数据大小(M)" width="130" sortable align="right">
          <template #default="{ row }">{{ formatMb(row.dataSizeMb) }}</template>
        </el-table-column>
        <el-table-column prop="indexSizeMb" label="索引大小(M)" width="130" sortable align="right">
          <template #default="{ row }">{{ formatMb(row.indexSizeMb) }}</template>
        </el-table-column>
        <el-table-column prop="totalSizeMb" label="总大小(M)" width="120" sortable align="right">
          <template #default="{ row }">{{ formatMb(row.totalSizeMb) }}</template>
        </el-table-column>
        <el-table-column prop="fragmentSizeMb" label="碎片大小(M)" width="130" sortable align="right">
          <template #default="{ row }">
            <el-tag :type="getFragmentTag(row.fragmentSizeMb)" effect="plain">
              {{ formatMb(row.fragmentSizeMb) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fragmentPercent" label="碎片率" width="110" sortable align="right">
          <template #default="{ row }">{{ formatPercent(row.fragmentPercent) }}</template>
        </el-table-column>
        <el-table-column prop="engine" label="表引擎" width="110" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getConnections, type Connection } from '../../api/connection'
import { getTableStats, getTableStatsExportUrl, type TableStats } from '../../api/tableStats'

const loading = ref(false)
const exportLoading = ref(false)
const connections = ref<Connection[]>([])
const tableData = ref<TableStats[]>([])

const searchForm = reactive<{
  connectionId?: number
  schema: string
  keyword: string
  minFragmentMb?: number
}>({
  connectionId: undefined,
  schema: '',
  keyword: '',
  minFragmentMb: undefined
})

const summary = computed(() => {
  return tableData.value.reduce(
    (acc, item) => {
      acc.tableCount += 1
      acc.dataSizeMb += item.dataSizeMb || 0
      acc.indexSizeMb += item.indexSizeMb || 0
      acc.fragmentSizeMb += item.fragmentSizeMb || 0
      return acc
    },
    { tableCount: 0, dataSizeMb: 0, indexSizeMb: 0, fragmentSizeMb: 0 }
  )
})

const fetchConnections = async () => {
  const res = await getConnections({ enabled: true, page: 0, size: 200 })
  if (res.code === 200) {
    connections.value = res.data.content
    if (!searchForm.connectionId && connections.value.length > 0) {
      searchForm.connectionId = connections.value[0].id
      await fetchStats()
    }
  }
}

const fetchStats = async () => {
  if (!searchForm.connectionId) return
  loading.value = true
  try {
    const res = await getTableStats({
      connectionId: searchForm.connectionId,
      schema: searchForm.schema || undefined,
      keyword: searchForm.keyword || undefined,
      minFragmentMb: searchForm.minFragmentMb
    })
    if (res.code === 200) {
      tableData.value = res.data
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取表空间信息失败')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.schema = ''
  searchForm.keyword = ''
  searchForm.minFragmentMb = undefined
  fetchStats()
}

const currentParams = () => ({
  connectionId: searchForm.connectionId as number,
  schema: searchForm.schema || undefined,
  keyword: searchForm.keyword || undefined,
  minFragmentMb: searchForm.minFragmentMb
})

const handleExport = async () => {
  if (!searchForm.connectionId) return
  exportLoading.value = true
  try {
    const response = await fetch(getTableStatsExportUrl(currentParams()), {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${localStorage.getItem('token')}`
      }
    })
    if (!response.ok) {
      throw new Error('导出失败')
    }

    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url

    const contentDisposition = response.headers.get('Content-Disposition')
    let filename = `表空间分析_${new Date().toISOString().slice(0, 19).replace(/[:-]/g, '')}.xlsx`
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

const formatInteger = (value?: number) => value == null ? '-' : Math.round(value).toLocaleString('zh-CN')
const formatMb = (value?: number) => value == null ? '-' : value.toFixed(2)
const formatPercent = (value?: number) => value == null ? '-' : `${value.toFixed(2)}%`
const getFragmentTag = (value: number) => {
  if (value >= 1024) return 'danger'
  if (value >= 100) return 'warning'
  return 'info'
}

onMounted(fetchConnections)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.search-form {
  margin-bottom: 16px;
}

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
</style>
