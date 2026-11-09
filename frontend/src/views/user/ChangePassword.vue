<template>
  <div class="change-password">
    <el-card>
      <template #header>
        <span class="card-header">修改密码</span>
      </template>
      <el-form :model="passwordForm" :rules="rules" ref="passwordFormRef" label-width="100px" style="max-width: 500px;">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleChangePassword">修改密码</el-button>
        </el-form-item>
      </el-form>
      <el-alert title="新密码需为 8–20 位，并同时包含字母和数字。" type="info" :closable="false" show-icon />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const passwordFormRef = ref(null)
const saving = ref(false)
const router = useRouter()
const userStore = useUserStore()

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: /^(?=.*[a-zA-Z])(?=.*\d)[a-zA-Z\d@$!%*?&]{8,20}$/, message: '请输入8–20位且包含字母和数字的密码', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value || saving.value) return
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (passwordForm.oldPassword === passwordForm.newPassword) {
    ElMessage.warning('新密码不能与原密码相同')
    return
  }
  if (!userStore.userInfo?.id) {
    ElMessage.error('未获取到当前用户信息，请重新登录')
    return
  }
  saving.value = true
  try {
    await changePassword(userStore.userInfo.id, passwordForm.oldPassword, passwordForm.newPassword)
    userStore.logoutAction()
    await router.replace('/login')
    ElMessage.success('密码修改成功，请使用新密码重新登录')
  } catch (error) {
    if (!error.messageShown) ElMessage.error(error.message || '密码修改失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.change-password {
  padding: 10px;
}

.card-header {
  font-weight: bold;
  font-size: 16px;
}

.el-alert {
  max-width: 500px;
}
</style>
