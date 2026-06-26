<template>
  <div class="archive-task-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>归档任务</span>
          <el-button type="primary" @click="$router.push('/archive-tasks/create')">
            <el-icon><Plus /></el-icon>
            新建归档任务
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="任务名称">
          <el-input v-model="searchForm.name" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="name" label="任务名称" min-width="160" />
        <el-table-column prop="sourceConnectionName" label="源连接" min-width="140" />
        <el-table-column prop="destConnectionName" label="目标连接" min-width="140" />
        <el-table-column label="步骤数" width="90">
          <template #default="{ row }">{{ row.steps?.length || 0 }}</template>
        </el-table-column>
        <el-table-column prop="cronExpression" label="定时表达式" width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="handleRun(row)">执行</el-button>
            <el-button link type="primary" @click="$router.push(`/archive-tasks/${row.id}/monitor`)">监控</el-button>
            <el-button link type="primary" @click="$router.push(`/archive-tasks/${row.id}/edit`)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteArchiveTask, getArchiveTasks, runArchiveTask, type ArchiveTask } from '../../api/archive'

const router = useRouter()
const loading = ref(false)
const tableData = ref<ArchiveTask[]>([])
const searchForm = reactive({ name: '', status: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getArchiveTasks({
      ...searchForm,
      page: pagination.page - 1,
      size: pagination.size
    })
    if (res.code === 200) {
      tableData.value = res.data.content
      pagination.total = res.data.totalElements
    }
  } finally {
    loading.value = false
  }
}

const handleRun = async (row: ArchiveTask) => {
  await ElMessageBox.confirm('确定要执行该归档任务吗？', '提示')
  const res = await runArchiveTask(row.id!)
  if (res.code === 200) {
    ElMessage.success('归档任务已启动')
    router.push({ path: `/archive-tasks/${row.id}/monitor`, query: { executionId: res.data.id } })
  }
}

const handleDelete = async (row: ArchiveTask) => {
  await ElMessageBox.confirm('确定要删除该归档任务吗？', '提示', { type: 'warning' })
  await deleteArchiveTask(row.id!)
  ElMessage.success('删除成功')
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.search-form {
  margin-bottom: 20px;
}
</style>
