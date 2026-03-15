<template>
  <div class="user-list">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        新增用户
      </el-button>
    </div>

    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="全部" clearable>
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.enabled" placeholder="全部" clearable>
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchUsers">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="userList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="role" label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              @change="handleStatusChange(row)"
              :loading="row.statusLoading"
              :disabled="row.username === 'admin'"
            />
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" width="180">
          <template #default="{ row }">
            {{ formatDate(row.lastLoginTime) || '从未登录' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="editUser(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button type="warning" link @click="resetPassword(row)">
              <el-icon><Key /></el-icon>
              重置密码
            </el-button>
            <el-popconfirm
              v-if="row.username !== 'admin'"
              title="确定要删除此用户吗？"
              @confirm="deleteUser(row)"
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
          @size-change="fetchUsers"
          @current-change="fetchUsers"
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :disabled="!!form.id"
          />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input
            v-model="form.password"
            placeholder="请输入密码"
            type="password"
            show-password
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
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

    <!-- 重置密码对话框 -->
    <el-dialog
      v-model="resetPwdVisible"
      title="重置密码"
      width="400px"
      destroy-on-close
    >
      <el-form
        ref="resetPwdFormRef"
        :model="resetPwdForm"
        :rules="resetPwdRules"
        label-width="80px"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="resetPwdForm.newPassword"
            placeholder="请输入新密码"
            type="password"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="resetPwdForm.confirmPassword"
            placeholder="请再次输入新密码"
            type="password"
            show-password
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="resetPwdVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPassword" :loading="resetPwdLoading">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Edit, Delete, Key } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { userApi } from '@/api/user'

interface User {
  id?: number
  username: string
  password?: string
  email: string
  role: 'ADMIN' | 'USER'
  enabled: boolean
  lastLoginTime?: string
  createdAt?: string
  statusLoading?: boolean
}

const loading = ref(false)
const userList = ref<User[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const resetPwdVisible = ref(false)
const resetPwdLoading = ref(false)
const resetPwdFormRef = ref<FormInstance>()
const currentUserId = ref<number>()

const searchForm = reactive({
  username: '',
  role: undefined as string | undefined,
  enabled: undefined as boolean | undefined
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const form = reactive<User>({
  username: '',
  password: '',
  email: '',
  role: 'USER',
  enabled: true
})

const resetPwdForm = reactive({
  newPassword: '',
  confirmPassword: ''
})

const formRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为3-50个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度为6-100个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const validateConfirmPassword = (rule: any, value: string, callback: any) => {
  if (value !== resetPwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const resetPwdRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度为6-100个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      ...searchForm
    }
    const res = await userApi.getList(params)
    userList.value = res.data.content
    pagination.total = res.data.totalElements
  } catch (error) {
    console.error('获取用户列表失败:', error)
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.username = ''
  searchForm.role = undefined
  searchForm.enabled = undefined
  pagination.page = 1
  fetchUsers()
}

const showCreateDialog = () => {
  dialogTitle.value = '新增用户'
  resetForm()
  dialogVisible.value = true
}

const editUser = (row: User) => {
  dialogTitle.value = '编辑用户'
  Object.assign(form, row)
  form.password = ''
  dialogVisible.value = true
}

const resetForm = () => {
  form.id = undefined
  form.username = ''
  form.password = ''
  form.email = ''
  form.role = 'USER'
  form.enabled = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (form.id) {
          await userApi.update(form.id, {
            email: form.email,
            role: form.role,
            enabled: form.enabled
          })
          ElMessage.success('更新成功')
        } else {
          await userApi.create(form)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        fetchUsers()
      } catch (error) {
        console.error('保存用户失败:', error)
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const handleStatusChange = async (row: User) => {
  row.statusLoading = true
  try {
    await userApi.update(row.id!, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '已启用' : '已禁用')
  } catch (error) {
    row.enabled = !row.enabled
    console.error('更新状态失败:', error)
  } finally {
    row.statusLoading = false
  }
}

const resetPassword = (row: User) => {
  currentUserId.value = row.id
  resetPwdForm.newPassword = ''
  resetPwdForm.confirmPassword = ''
  resetPwdVisible.value = true
}

const submitResetPassword = async () => {
  if (!resetPwdFormRef.value) return
  await resetPwdFormRef.value.validate(async (valid) => {
    if (valid) {
      resetPwdLoading.value = true
      try {
        await userApi.resetPassword(currentUserId.value!, resetPwdForm.newPassword)
        ElMessage.success('密码重置成功')
        resetPwdVisible.value = false
      } catch (error) {
        console.error('重置密码失败:', error)
      } finally {
        resetPwdLoading.value = false
      }
    }
  })
}

const deleteUser = async (row: User) => {
  try {
    await userApi.delete(row.id!)
    ElMessage.success('删除成功')
    fetchUsers()
  } catch (error) {
    console.error('删除用户失败:', error)
  }
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped>
.user-list {
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
