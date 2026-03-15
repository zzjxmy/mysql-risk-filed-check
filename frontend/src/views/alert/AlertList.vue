<template>
  <div class="alert-list">
    <div class="page-header">
      <h2>告警配置</h2>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        新增配置
      </el-button>
    </div>

    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="配置名称">
          <el-input v-model="searchForm.name" placeholder="请输入配置名称" clearable />
        </el-form-item>
        <el-form-item label="告警类型">
          <el-select v-model="searchForm.type" placeholder="全部" clearable>
            <el-option label="全部" value="" />
            <el-option label="钉钉机器人" value="DINGTALK" />
            <el-option label="邮件" value="EMAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.enabled" placeholder="全部" clearable>
            <el-option label="全部" value="" />
            <el-option label="启用" value="true" />
            <el-option label="禁用" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchAlerts">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="alertList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="配置名称" min-width="150" />
        <el-table-column prop="alertType" label="告警类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.alertType === 'DINGTALK' ? 'primary' : 'success'">
              {{ row.alertType === 'DINGTALK' ? '钉钉机器人' : '邮件' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="配置详情" min-width="200">
          <template #default="{ row }">
            <span v-if="row.alertType === 'DINGTALK'">
              Webhook: {{ maskWebhook(getConfigValue(row.config, 'webhook')) }}
            </span>
            <span v-else>
              收件人: {{ getConfigValue(row.config, 'emailRecipients') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              @change="handleStatusChange(row)"
              :loading="row.statusLoading"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="testAlert(row)">
              <el-icon><Bell /></el-icon>
              测试
            </el-button>
            <el-button type="primary" link @click="editAlert(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-popconfirm
              title="确定要删除此告警配置吗？"
              @confirm="deleteAlert(row)"
            >
              <template #reference>
                <el-button type="danger" link>
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchAlerts"
          @current-change="fetchAlerts"
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入配置名称" />
        </el-form-item>
        <el-form-item label="告警类型" prop="type">
          <el-radio-group v-model="form.type" @change="handleTypeChange">
            <el-radio label="DINGTALK">钉钉机器人</el-radio>
            <el-radio label="EMAIL">邮件</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 钉钉配置 -->
        <template v-if="form.type === 'DINGTALK'">
          <el-form-item label="Webhook地址" prop="webhookUrl">
            <el-input
              v-model="form.webhookUrl"
              placeholder="请输入钉钉机器人Webhook地址"
              type="textarea"
              :rows="2"
            />
          </el-form-item>
          <el-form-item label="加签密钥" prop="secret">
            <el-input
              v-model="form.secret"
              placeholder="如果启用了加签，请输入密钥"
              show-password
            />
          </el-form-item>
          <el-form-item label="@手机号">
            <el-input
              v-model="form.atMobiles"
              placeholder="多个手机号用逗号分隔"
            />
          </el-form-item>
        </template>

        <!-- 邮件配置 -->
        <template v-if="form.type === 'EMAIL'">
          <el-form-item label="SMTP服务器" prop="smtpHost">
            <el-input v-model="form.smtpHost" placeholder="如: smtp.163.com" />
          </el-form-item>
          <el-form-item label="SMTP端口" prop="smtpPort">
            <el-input-number v-model="form.smtpPort" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="发件人邮箱" prop="senderEmail">
            <el-input v-model="form.senderEmail" placeholder="请输入发件人邮箱" />
          </el-form-item>
          <el-form-item label="邮箱密码" prop="senderPassword">
            <el-input
              v-model="form.senderPassword"
              placeholder="请输入邮箱密码或授权码"
              show-password
            />
          </el-form-item>
          <el-form-item label="收件人" prop="emailRecipients">
            <el-input
              v-model="form.emailRecipients"
              placeholder="多个收件人用逗号分隔"
              type="textarea"
              :rows="2"
            />
          </el-form-item>
          <el-form-item label="启用SSL">
            <el-switch v-model="form.useSsl" />
          </el-form-item>
        </template>

        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Edit, Delete, Bell } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { alertApi } from '@/api/alert'

interface AlertConfig {
  id?: number
  name: string
  alertType: 'DINGTALK' | 'EMAIL'
  config?: string
  enabled: boolean
  remark?: string
  createdAt?: string
  statusLoading?: boolean
}

const loading = ref(false)
const alertList = ref<AlertConfig[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增告警配置')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const searchForm = reactive({
  name: '',
  type: '',
  enabled: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const form = reactive<AlertConfig>({
  name: '',
  type: 'DINGTALK',
  webhookUrl: '',
  secret: '',
  atMobiles: '',
  smtpHost: '',
  smtpPort: 465,
  senderEmail: '',
  senderPassword: '',
  emailRecipients: '',
  useSsl: true,
  enabled: true
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择告警类型', trigger: 'change' }],
  webhookUrl: [{ required: true, message: '请输入Webhook地址', trigger: 'blur' }],
  smtpHost: [{ required: true, message: '请输入SMTP服务器', trigger: 'blur' }],
  smtpPort: [{ required: true, message: '请输入SMTP端口', trigger: 'blur' }],
  senderEmail: [
    { required: true, message: '请输入发件人邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  senderPassword: [{ required: true, message: '请输入邮箱密码', trigger: 'blur' }],
  emailRecipients: [{ required: true, message: '请输入收件人', trigger: 'blur' }]
}

const fetchAlerts = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page - 1,
      size: pagination.size
    }
    if (searchForm.name) params.name = searchForm.name
    if (searchForm.type) params.type = searchForm.type
    if (searchForm.enabled !== null) params.enabled = searchForm.enabled
    
    const res = await alertApi.getList(params)
    alertList.value = res.data || []
    pagination.total = res.data?.length || 0
  } catch (error) {
    console.error('获取告警配置列表失败:', error)
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.name = ''
  searchForm.type = ''
  searchForm.enabled = ''
  pagination.page = 1
  fetchAlerts()
}

const showCreateDialog = () => {
  dialogTitle.value = '新增告警配置'
  resetForm()
  dialogVisible.value = true
}

const editAlert = (row: AlertConfig) => {
  dialogTitle.value = '编辑告警配置'
  // Reset form first
  resetForm()
  // Set basic fields
  form.id = row.id
  form.name = row.name
  form.type = row.alertType || 'DINGTALK'
  form.enabled = row.enabled
  
  // Parse config JSON
  if (row.config) {
    try {
      const config = JSON.parse(row.config)
      if (form.type === 'DINGTALK') {
        form.webhookUrl = config.webhook || ''
        form.secret = config.secret || ''
        form.atMobiles = config.atMobiles || ''
      } else if (form.type === 'EMAIL') {
        form.smtpHost = config.smtpHost || ''
        form.smtpPort = config.smtpPort || 465
        form.senderEmail = config.senderEmail || ''
        form.senderPassword = config.senderPassword || ''
        form.emailRecipients = config.emailRecipients || ''
        form.useSsl = config.useSsl !== undefined ? config.useSsl : true
      }
    } catch (e) {
      console.error('Failed to parse config:', e)
    }
  }
  
  dialogVisible.value = true
}

const resetForm = () => {
  form.id = undefined
  form.name = ''
  form.type = 'DINGTALK'
  form.webhookUrl = ''
  form.secret = ''
  form.atMobiles = ''
  form.smtpHost = ''
  form.smtpPort = 465
  form.senderEmail = ''
  form.senderPassword = ''
  form.emailRecipients = ''
  form.useSsl = true
  form.enabled = true
}

const handleTypeChange = () => {
  formRef.value?.clearValidate()
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        // Convert form data to backend format
        const configData: any = {
          name: form.name,
          alertType: form.type,
          enabled: form.enabled,
          remark: ''
        }
        
        // Build config JSON based on type
        if (form.type === 'DINGTALK') {
          configData.config = JSON.stringify({
            webhook: form.webhookUrl,
            secret: form.secret,
            atMobiles: form.atMobiles
          })
        } else if (form.type === 'EMAIL') {
          configData.config = JSON.stringify({
            smtpHost: form.smtpHost,
            smtpPort: form.smtpPort,
            senderEmail: form.senderEmail,
            senderPassword: form.senderPassword,
            emailRecipients: form.emailRecipients,
            useSsl: form.useSsl
          })
        }
        
        if (form.id) {
          await alertApi.update(form.id, configData)
          ElMessage.success('更新成功')
        } else {
          await alertApi.create(configData)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        fetchAlerts()
      } catch (error) {
        console.error('保存告警配置失败:', error)
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const handleStatusChange = async (row: AlertConfig) => {
  row.statusLoading = true
  try {
    // Must include alertType for backend enum conversion
    await alertApi.update(row.id!, { 
      enabled: row.enabled,
      alertType: row.alertType,
      name: row.name,
      config: row.config
    })
    ElMessage.success(row.enabled ? '已启用' : '已禁用')
  } catch (error) {
    row.enabled = !row.enabled
    console.error('更新状态失败:', error)
  } finally {
    row.statusLoading = false
  }
}

const testAlert = async (row: AlertConfig) => {
  try {
    await alertApi.test(row.id!)
    ElMessage.success('测试消息已发送，请检查接收情况')
  } catch (error) {
    console.error('发送测试消息失败:', error)
  }
}

const deleteAlert = async (row: AlertConfig) => {
  try {
    await alertApi.delete(row.id!)
    ElMessage.success('删除成功')
    fetchAlerts()
  } catch (error) {
    console.error('删除告警配置失败:', error)
  }
}

const maskWebhook = (url?: string) => {
  if (!url) return ''
  if (url.length > 50) {
    return url.substring(0, 30) + '...' + url.substring(url.length - 10)
  }
  return url
}

const getConfigValue = (configJson?: string, key: string): string => {
  if (!configJson) return ''
  try {
    const config = JSON.parse(configJson)
    return config[key] || ''
  } catch (e) {
    return ''
  }
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchAlerts()
})
</script>

<style scoped>
.alert-list {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
}

.search-card {
  margin-bottom: 20px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
