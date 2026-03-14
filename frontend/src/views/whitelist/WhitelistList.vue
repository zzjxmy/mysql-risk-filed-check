<template>
  <div class="whitelist-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>白名单管理</span>
          <el-button type="primary" @click="showDialog()">新增规则</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="rule" label="规则" />
        <el-table-column prop="ruleType" label="类型" width="100">
          <template #default="{ row }">
            {{ { DATABASE: '数据库', TABLE: '表', FIELD: '字段' }[row.ruleType] || row.ruleType }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="pagination.page" :total="pagination.total" @change="fetchData" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑规则' : '新增规则'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="规则" prop="rule">
          <el-input v-model="form.rule" placeholder="如：db.* 或 db.table.field" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const tableData = ref<any[]>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const form = reactive({ id: undefined as number | undefined, rule: '', enabled: true, remark: '' })
const rules: FormRules = { rule: [{ required: true, message: '请输入规则', trigger: 'blur' }] }

const fetchData = async () => {
  loading.value = true
  const res: any = await request.get('/whitelist', { params: { page: pagination.page - 1, size: pagination.size } })
  if (res.code === 200) { tableData.value = res.data.content; pagination.total = res.data.totalElements }
  loading.value = false
}

const showDialog = (row?: any) => {
  isEdit.value = !!row
  Object.assign(form, row ? { ...row } : { id: undefined, rule: '', enabled: true, remark: '' })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value?.validate(async (valid) => {
    if (valid) {
      if (isEdit.value) await request.put(`/whitelist/${form.id}`, form)
      else await request.post('/whitelist', form)
      ElMessage.success('保存成功')
      dialogVisible.value = false
      fetchData()
    }
  })
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm('确定删除?', '提示')
  await request.delete(`/whitelist/${row.id}`)
  ElMessage.success('删除成功')
  fetchData()
}

onMounted(() => { fetchData() })
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
