<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon :size="32"><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalRisks }}</div>
              <div class="stat-label">总风险数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon :size="32"><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.pendingRisks }}</div>
              <div class="stat-label">待处理</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" style="background: #67C23A;">
              <el-icon :size="32"><Check /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.resolvedRisks }}</div>
              <div class="stat-label">已解决</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" style="background: #909399;">
              <el-icon :size="32"><Hide /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.ignoredRisks }}</div>
              <div class="stat-label">已忽略</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="16">
        <el-card>
          <template #header>
            <span>风险趋势 (近30天)</span>
          </template>
          <div ref="trendChartRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>风险类型分布</span>
          </template>
          <div ref="typeChartRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import * as echarts from 'echarts'
import { getRiskStats } from '../api/risk'

const stats = reactive({
  totalRisks: 0,
  pendingRisks: 0,
  resolvedRisks: 0,
  ignoredRisks: 0,
  risksByType: {} as Record<string, number>,
  riskTrend: [] as Array<{ date: string; count: number }>
})

const trendChartRef = ref<HTMLDivElement>()
const typeChartRef = ref<HTMLDivElement>()

let trendChart: echarts.ECharts
let typeChart: echarts.ECharts

const fetchStats = async () => {
  try {
    const res = await getRiskStats()
    if (res.code === 200) {
      Object.assign(stats, res.data)
      renderCharts()
    }
  } catch (error) {
    console.error('Failed to fetch stats', error)
  }
}

const renderCharts = () => {
  // Trend chart
  if (trendChartRef.value) {
    if (!trendChart) {
      trendChart = echarts.init(trendChartRef.value)
    }
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: stats.riskTrend.map(item => item.date)
      },
      yAxis: { type: 'value' },
      series: [{
        data: stats.riskTrend.map(item => item.count),
        type: 'line',
        smooth: true,
        areaStyle: {}
      }]
    })
  }

  // Type distribution chart
  if (typeChartRef.value) {
    if (!typeChart) {
      typeChart = echarts.init(typeChartRef.value)
    }
    const typeNames: Record<string, string> = {
      INT_OVERFLOW: '整型溢出',
      DECIMAL_OVERFLOW: '小数溢出',
      Y2038: 'Y2038问题',
      STRING_TRUNCATION: '字符串截断',
      DATE_ANOMALY: '日期异常',
      OTHER: '其他'
    }
    typeChart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: Object.entries(stats.risksByType).map(([key, value]) => ({
          name: typeNames[key] || key,
          value
        }))
      }]
    })
  }
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.dashboard {
  width: 100%;
}

.stat-card {
  margin-bottom: 0;
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-info {
  margin-left: 16px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #333;
}

.stat-label {
  font-size: 14px;
  color: #999;
  margin-top: 4px;
}

.chart {
  width: 100%;
  height: 300px;
}
</style>
