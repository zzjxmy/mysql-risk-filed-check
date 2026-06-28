<template>
  <div class="archive-task-form">
    <el-card>
      <template #header>{{ isEdit ? '编辑归档任务' : '创建归档任务' }}</template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="源连接" prop="sourceConnectionId">
          <el-select v-model="form.sourceConnectionId" style="width: 100%" placeholder="请选择源库连接">
            <el-option v-for="conn in connections" :key="conn.id" :label="conn.name" :value="conn.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标连接" prop="destConnectionId">
          <el-select v-model="form.destConnectionId" style="width: 100%" placeholder="请选择归档库连接">
            <el-option v-for="conn in connections" :key="conn.id" :label="conn.name" :value="conn.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="定时表达式">
          <el-input v-model="form.cronExpression" placeholder="如：0 0 2 * * ? " />
        </el-form-item>
        <el-form-item label="告警配置">
          <el-select v-model="form.alertConfigIds" multiple style="width: 100%" placeholder="失败时通知">
            <el-option v-for="alert in alertConfigs" :key="alert.id" :label="alert.name" :value="alert.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>

        <el-divider content-position="left">预查询变量</el-divider>
        <div v-for="(variable, index) in form.variables" :key="index" class="block-row">
          <div class="block-title">
            <span>变量 {{ index + 1 }}</span>
            <el-button link type="danger" @click="removeVariable(index)">删除</el-button>
          </div>
          <el-form-item label="变量名">
            <el-input v-model="variable.name" placeholder="如 maxid" />
          </el-form-item>
          <el-form-item label="SELECT SQL">
            <el-input v-model="variable.querySql" type="textarea" :rows="3" placeholder="select max(id) from db.table where ..." />
          </el-form-item>
        </div>
        <el-button class="add-button" @click="addVariable">
          <el-icon><Plus /></el-icon>
          添加变量
        </el-button>

        <el-divider content-position="left">归档步骤</el-divider>
        <div v-for="(step, index) in form.steps" :key="index" class="block-row">
          <div class="block-title">
            <span>步骤 {{ index + 1 }}</span>
            <el-button link type="danger" :disabled="form.steps.length === 1" @click="removeStep(index)">删除</el-button>
          </div>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="步骤名称">
                <el-input v-model="step.name" placeholder="如 归档sys_kpi_log" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="删除源数据">
                <el-switch v-model="step.deleteSource" active-text="删除" inactive-text="只复制" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item>
                <template #label>
                  <span class="label-with-tip">
                    批量导入
                    <el-tooltip content="开启后使用 LOAD DATA LOCAL INFILE，目标库需开启 local_infile" placement="top">
                      <el-icon class="tip-icon"><InfoFilled /></el-icon>
                    </el-tooltip>
                  </span>
                </template>
                <el-switch v-model="step.bulkInsert" active-text="开启" inactive-text="关闭" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="源库">
                <el-input v-model="step.sourceDatabase" placeholder="hsq_online" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="源表">
                <el-input v-model="step.sourceTable" placeholder="sys_kpi_log" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标库">
                <el-input v-model="step.destDatabase" placeholder="legacy_hsq_online" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标表">
                <el-input v-model="step.destTable" placeholder="sys_kpi_log" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="归档条件">
            <el-input v-model="step.whereTemplate" type="textarea" :rows="2" placeholder="id < ${maxid}" />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="limit">
                <el-input-number v-model="step.limitSize" :min="1" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="progress">
                <el-input-number v-model="step.progressSize" :min="1" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="字符集">
                <el-input v-model="step.charset" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="额外参数">
            <el-input v-model="step.extraOptions" type="textarea" :rows="2" placeholder="每行一个参数，如 --why-quit" />
          </el-form-item>
        </div>
        <el-button class="add-button" @click="addStep">
          <el-icon><Plus /></el-icon>
          添加步骤
        </el-button>

        <el-form-item class="actions">
          <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { InfoFilled, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { alertApi, type AlertConfig } from '../../api/alert'
import { getConnections, type Connection } from '../../api/connection'
import { createArchiveTask, getArchiveTask, updateArchiveTask, type ArchiveStep, type ArchiveTask } from '../../api/archive'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const enabled = ref(true)
const connections = ref<Connection[]>([])
const alertConfigs = ref<AlertConfig[]>([])
const isEdit = computed(() => !!route.params.id)

const newStep = (): ArchiveStep => ({
  name: '',
  sourceDatabase: '',
  sourceTable: '',
  destDatabase: '',
  destTable: '',
  whereTemplate: '',
  deleteSource: true,
  charset: 'UTF8',
  limitSize: 5000,
  progressSize: 5000,
  bulkInsert: false,
  commitEach: true,
  extraOptions: '',
  enabled: true
})

const form = reactive<ArchiveTask>({
  name: '',
  sourceConnectionId: 0,
  destConnectionId: 0,
  cronExpression: '',
  status: 'ENABLED',
  alertConfigIds: [],
  variables: [],
  steps: [newStep()]
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  sourceConnectionId: [{ required: true, message: '请选择源连接', trigger: 'change' }],
  destConnectionId: [{ required: true, message: '请选择目标连接', trigger: 'change' }]
}

const addVariable = () => form.variables.push({ name: '', querySql: '', enabled: true })
const removeVariable = (index: number) => form.variables.splice(index, 1)
const addStep = () => form.steps.push(newStep())
const removeStep = (index: number) => form.steps.splice(index, 1)

const fetchOptions = async () => {
  const [connRes, alertRes] = await Promise.all([
    getConnections({ enabled: true, size: 100 }),
    alertApi.getList({ enabled: true })
  ])
  if (connRes.code === 200) connections.value = connRes.data.content
  if (alertRes.code === 200) alertConfigs.value = alertRes.data || []
}

const fetchTask = async () => {
  if (!isEdit.value) return
  const res = await getArchiveTask(Number(route.params.id))
  if (res.code === 200) {
    Object.assign(form, res.data)
    enabled.value = res.data.status === 'ENABLED'
    if (!form.variables) form.variables = []
    if (!form.steps || form.steps.length === 0) form.steps = [newStep()]
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return
    submitting.value = true
    try {
      const data = { ...form, status: enabled.value ? 'ENABLED' : 'DISABLED' }
      if (isEdit.value) {
        await updateArchiveTask(Number(route.params.id), data)
      } else {
        await createArchiveTask(data)
      }
      ElMessage.success('保存成功')
      router.push('/archive-tasks')
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    } finally {
      submitting.value = false
    }
  })
}

onMounted(async () => {
  await fetchOptions()
  await fetchTask()
})
</script>

<style scoped>
.archive-task-form {
  max-width: 980px;
}
.block-row {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 16px 16px 0;
  margin-bottom: 14px;
  background: #fff;
}
.block-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 600;
}
.label-with-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.tip-icon {
  color: #909399;
  cursor: help;
}
.add-button {
  margin-bottom: 18px;
}
.actions {
  margin-top: 24px;
}
</style>
