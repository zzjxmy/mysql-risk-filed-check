<template>
  <div class="login-container">
    <div class="login-box">
      <h2 class="title">MySQL字段容量风险检查平台</h2>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item v-if="ldapEnabled">
          <el-checkbox v-model="loginForm.useLdap">
            使用LDAP登录
          </el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            {{ loginForm.useLdap ? 'LDAP登录' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
      <div class="tips">
        <span v-if="!loginForm.useLdap">默认账号: admin / admin123</span>
        <span v-else>使用LDAP账号密码登录</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '../../stores/user'
import { getLdapStatus } from '../../api/ldap'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const ldapEnabled = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  useLdap: false
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// Check if LDAP is enabled
const checkLdapStatus = async () => {
  try {
    const { data } = await getLdapStatus()
    ldapEnabled.value = data?.enabled || false
  } catch (error) {
    // LDAP config might not exist
    ldapEnabled.value = false
  }
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userStore.login(loginForm.username, loginForm.password, loginForm.useLdap)
        ElMessage.success('登录成功')
        router.push('/dashboard')
      } catch (error: any) {
        ElMessage.error(error.message || '登录失败')
      } finally {
        loading.value = false
      }
    }
  })
}

onMounted(() => {
  checkLdapStatus()
})
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.title {
  text-align: center;
  margin-bottom: 30px;
  color: #333;
  font-size: 20px;
}

.login-form {
  width: 100%;
}

.login-btn {
  width: 100%;
}

.tips {
  margin-top: 20px;
  text-align: center;
  color: #999;
  font-size: 12px;
}
</style>
