<template>
  <div class="task-form">
    <el-card>
      <template #header>
        <span>{{ isEdit ? '编辑任务' : '创建任务' }}</span>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" style="max-width: 600px;">
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="数据库连接" prop="connectionId">
          <el-select v-model="form.connectionId" placeholder="请选择" style="width: 100%;">
            <el-option v-for="conn in connections" :key="conn.id" :label="conn.name" :value="conn.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据库模式">
          <el-input v-model="form.dbPattern" placeholder="如：db1,db2 或 db_*" />
        </el-form-item>
        <el-form-item label="表模式">
          <el-input v-model="form.tablePattern" placeholder="如：t_* 或留空检查所有表" />
        </el-form-item>
        <el-form-item label="风险阈值">
          <el-input-number v-model="form.thresholdPct" :min="1" :max="100" />
          <span style="margin-left: 10px;">%</span>
        </el-form-item>
        <el-form-item label="Y2038告警年">
          <el-input-number v-model="form.y2038WarningYear" :min="2024" :max="2038" />
        </el-form-item>
        <el-form-item label="强制全表扫描">
          <el-switch v-model="form.fullScan" />
        </el-form-item>
        <el-form-item label="抽样条数">
          <el-input-number v-model="form.sampleSize" :min="100" :max="10000" />
        </el-form-item>
        <el-form-item label="大表阈值">
          <el-input-number v-model="form.maxTableRows" :min="10000" />
        </el-form-item>
        <el-form-item label="定时表达式">
          <el-input v-model="form.cronExpression" placeholder="如：0 0 2 * * ? (每天凌晨2点)" />
        </el-form-item>
        <el-form-item label="白名单类型">
          <el-select v-model="form.whitelistType" style="width: 100%;">
            <el-option label="不使用白名单" value="NONE" />
            <el-option label="使用全局白名单" value="GLOBAL" />
            <el-option label="使用自定义白名单" value="CUSTOM" />
          </el-select>
          <div class="form-tip">选择全局白名单将应用白名单管理中的规则，自定义白名单可在下方输入</div>
        </el-form-item>
        <el-form-item label="自定义白名单" v-if="form.whitelistType === 'CUSTOM'">
          <el-input
            v-model="form.customWhitelist"
            type="textarea"
            :rows="4"
            placeholder="# 每行一个规则，支持通配符 * 和 ?
# 格式：数据库.表名 或 数据库.表名.字段名
db1.table1
db2.*
test.*"
          />
        </el-form-item>
        <el-form-item label="告警配置">
          <el-select
            v-model="form.alertConfigIds"
            multiple
            placeholder="请选择告警配置（可多选）"
            style="width: 100%;"
          >
            <el-option
              v-for="alert in alertConfigs"
              :key="alert.id"
              :label="alert.name + ' (' + (alert.alertType === 'DINGTALK' ? '钉钉' : '邮件') + ')'"
              :value="alert.id"
            />
          </el-select>
          <div class="form-tip">选择任务执行异常时要通知的告警配置，不选则不会发送告警</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getTask, createTask, updateTask, type Task } from '../../api/task'
import { getConnections, type Connection } from '../../api/connection'
import { alertApi, type AlertConfig } from '../../api/alert'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const connections = ref<Connection[]>([])
const alertConfigs = ref<AlertConfig[]>([])
const enabled = ref(true)

const isEdit = computed(() => !!route.params.id)

const form = reactive<Task>({
  name: '',
  connectionId: 0,
  dbPattern: '',
  tablePattern: '',
  fullScan: false,
  sampleSize: 1000,
  maxTableRows: 1000000,
  thresholdPct: 90,
  y2038WarningYear: 2030,
  cronExpression: '',
  whitelistType: 'NONE',
  alertConfigIds: []
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  connectionId: [{ required: true, message: '请选择数据库连接', trigger: 'change' }]
}

const fetchConnections = async () => {
  const res = await getConnections({ enabled: true, size: 100 })
  if (res.code === 200) {
    connections.value = res.data.content
  }
}

const fetchAlertConfigs = async () => {
  const res = await alertApi.getList({ enabled: true, size: 100 })
  if (res.code === 200) {
    alertConfigs.value = res.data || []
  }
}

const fetchTask = async () => {
  if (!isEdit.value) return
  const res = await getTask(Number(route.params.id))
  if (res.code === 200) {
    Object.assign(form, res.data)
    enabled.value = res.data.status === 'ENABLED'
    // Ensure alertConfigIds is an array
    if (!form.alertConfigIds) {
      form.alertConfigIds = []
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        const data = { ...form, status: enabled.value ? 'ENABLED' : 'DISABLED' }
        if (isEdit.value) {
          await updateTask(Number(route.params.id), data)
        } else {
          await createTask(data)
        }
        ElMessage.success('保存成功')
        router.push('/tasks')
      } catch (error: any) {
        ElMessage.error(error.message || '保存失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

onMounted(() => {
  fetchConnections()
  fetchAlertConfigs()
  fetchTask()
})
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}
</style>
