<template>
  <div class="ldap-config">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>LDAP认证配置</span>
          <el-tag v-if="form.enabled" type="success" size="small">已启用</el-tag>
          <el-tag v-else type="info" size="small">未启用</el-tag>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px" v-loading="loading">
        <el-form-item label="启用LDAP认证">
          <el-switch v-model="form.enabled" />
          <div class="form-tip">启用后，用户可使用LDAP账号登录系统</div>
        </el-form-item>

        <el-divider content-position="left">服务器配置</el-divider>

        <el-form-item label="LDAP服务器地址" prop="url">
          <el-input v-model="form.url" placeholder="ldap://localhost:389 或 ldaps://localhost:636">
            <template #prepend>
              <el-select v-model="urlProtocol" style="width: 100px">
                <el-option label="ldap://" value="ldap://" />
                <el-option label="ldaps://" value="ldaps://" />
              </el-select>
            </template>
          </el-input>
          <div class="form-tip">示例: ldap://172.16.20.16:389 或 ldaps://server:636</div>
        </el-form-item>

        <el-form-item label="Base DN" prop="baseDn">
          <el-input v-model="form.baseDn" placeholder="dc=example,dc=com" />
        </el-form-item>

        <el-form-item label="管理员DN">
          <el-input v-model="form.username" placeholder="cn=admin,dc=example,dc=com" />
          <div class="form-tip">用于搜索用户的LDAP管理员账号（可选）</div>
        </el-form-item>

        <el-form-item label="管理员密码">
          <el-input v-model="form.password" type="password" show-password placeholder="输入新密码或留空保持原密码" />
          <div class="form-tip">用于搜索用户的LDAP管理员密码</div>
        </el-form-item>

        <el-divider content-position="left">用户搜索配置</el-divider>

        <el-form-item label="用户搜索基础">
          <el-input v-model="form.userSearchBase" placeholder="ou=users" />
          <div class="form-tip">用户搜索的起始节点</div>
        </el-form-item>

        <el-form-item label="用户搜索过滤器">
          <el-input v-model="form.userSearchFilter" placeholder="(uid={0})" />
          <div class="form-tip">{0}将被替换为用户名，如：(uid={0}) 或 (sAMAccountName={0})</div>
        </el-form-item>

        <el-divider content-position="left">属性映射</el-divider>

        <el-form-item label="邮箱属性">
          <el-input v-model="form.emailAttribute" placeholder="mail" />
        </el-form-item>

        <el-form-item label="显示名属性">
          <el-input v-model="form.displayNameAttribute" placeholder="displayName 或 cn" />
        </el-form-item>

        <el-form-item label="默认角色">
          <el-select v-model="form.defaultRole" style="width: 100%;">
            <el-option label="普通用户 (USER)" value="USER" />
            <el-option label="管理员 (ADMIN)" value="ADMIN" />
          </el-select>
          <div class="form-tip">LDAP用户首次登录时分配的默认角色</div>
        </el-form-item>

        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="配置说明" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSave" :loading="saving">保存配置</el-button>
          <el-button @click="handleTest" :loading="testing">测试连接</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getLdapConfig, saveLdapConfig, testLdapConnection, type LdapConfig } from '@/api/ldap'

const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const urlProtocol = ref('ldap://')

const form = reactive<LdapConfig>({
  enabled: false,
  url: '',
  baseDn: '',
  username: '',
  password: '',
  userSearchBase: '',
  userSearchFilter: '(uid={0})',
  groupSearchBase: '',
  groupSearchFilter: '',
  emailAttribute: 'mail',
  displayNameAttribute: 'displayName',
  defaultRole: 'USER',
  description: ''
})

// Computed full URL
const fullUrl = computed(() => {
  if (!form.url) return ''
  // Remove protocol if user accidentally included it
  const urlWithoutProtocol = form.url.replace(/^ldap:\/\//, '').replace(/^ldaps:\/\//, '')
  return urlProtocol.value + urlWithoutProtocol
})

const rules: FormRules = {
  url: [
    { required: true, message: '请输入LDAP服务器地址', trigger: 'blur' }
  ],
  baseDn: [
    { required: true, message: '请输入Base DN', trigger: 'blur' }
  ]
}

const loadConfig = async () => {
  loading.value = true
  try {
    const { data } = await getLdapConfig()
    if (data) {
      Object.assign(form, data)
      // Parse protocol from URL
      if (form.url) {
        if (form.url.startsWith('ldaps://')) {
          urlProtocol.value = 'ldaps://'
          form.url = form.url.substring(8)
        } else if (form.url.startsWith('ldap://')) {
          urlProtocol.value = 'ldap://'
          form.url = form.url.substring(7)
        }
      }
    }
  } catch (error) {
    console.error('Failed to load LDAP config:', error)
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    saving.value = true
    try {
      // Use full URL with protocol
      const dataToSave = { ...form, url: fullUrl.value }
      await saveLdapConfig(dataToSave)
      ElMessage.success('LDAP配置保存成功')
      await loadConfig()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}

const handleTest = async () => {
  if (!form.url || !form.baseDn) {
    ElMessage.warning('请先填写LDAP服务器地址和Base DN')
    return
  }

  testing.value = true
  try {
    // Use full URL with protocol
    const dataToTest = { ...form, url: fullUrl.value }
    const { data } = await testLdapConnection(dataToTest)
    if (data === null) {
      ElMessage.success('LDAP连接测试成功')
    } else {
      ElMessage.error('LDAP连接测试失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '连接测试失败')
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.ldap-config {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

:deep(.el-divider__text) {
  font-weight: 500;
  color: #606266;
}
</style>
