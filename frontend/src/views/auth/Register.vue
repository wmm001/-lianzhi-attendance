<template>
  <div class="register-container">
    <el-card class="register-card">
      <template #header>
        <div class="register-heading">
          <span class="emblem-crop"><img :src="hnuLogo" alt="湖南大学校徽" /></span>
          <div><small>湖南大学智慧校园</small><h2 class="card-title">学生账号注册</h2></div>
        </div>
      </template>
      <el-form :model="registerForm" :rules="rules" ref="registerFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="registerForm.username" placeholder="4–20位字母、数字或下划线" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="8–20位，包含大小写字母和数字" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="registerForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="学号" prop="studentId" v-if="registerForm.role === 'STUDENT'">
          <el-input v-model="registerForm.studentId" placeholder="请输入学号" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-tag type="success">学生</el-tag>
          <span class="role-tip">教师和管理员账号由管理员统一创建</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleRegister" style="width: 100%;" :loading="loading">注册</el-button>
        </el-form-item>
      </el-form>
      <div class="login-link">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'
import hnuLogo from '@/assets/brand/hnu-logo.png'

const router = useRouter()
const registerFormRef = ref(null)
const loading = ref(false)

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  studentId: '',
  role: 'STUDENT'
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,20}$/, message: '用户名须为4–20位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d@$!%*?&]{8,20}$/, message: '密码须为8–20位，并包含大小写字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  studentId: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]{3,20}$/, message: '学号须为3–20位字母、数字、下划线或短横线', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await register({
          username: registerForm.username,
          password: registerForm.password,
          realName: registerForm.realName,
          studentId: registerForm.studentId,
          role: registerForm.role
        })
        ElMessage.success('注册成功，请登录')
        router.push('/login')
      } catch (error) {
        if (!error.messageShown) ElMessage.error(error.message || '注册失败')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f6f1ed;
  background-image: radial-gradient(circle at 16% 18%, rgba(177,47,53,.13), transparent 31%), radial-gradient(circle at 84% 82%, rgba(198,161,91,.13), transparent 28%);
}

.register-card {
  width: 400px;
  border-radius: 18px;
  border-top: 4px solid var(--brand) !important;
  box-shadow: 0 24px 64px rgba(83,25,29,.13) !important;
}

.register-heading { display:flex; align-items:center; justify-content:center; gap:13px; }
.register-heading small { display:block; color:var(--brand); font-size:12px; margin-bottom:3px; letter-spacing:.5px; }
.emblem-crop { position:relative; width:52px; height:52px; flex:0 0 52px; overflow:hidden; border-radius:50%; background:#fff; }
.emblem-crop img { position:absolute; height:52px; width:auto; max-width:none; left:0; top:0; }

.card-title {
  margin: 0;
  text-align: center;
  color: var(--text);
}

.login-link {
  text-align: center;
  margin-top: 10px;
  color: #666;
}

.login-link a {
  color: var(--brand);
  text-decoration: none;
}
.role-tip { margin-left: 10px; color: #98a2b3; font-size: 12px; }
@media(max-width:600px){.register-container{align-items:flex-start;padding:calc(24px + env(safe-area-inset-top)) 14px 24px;border-top:6px solid var(--brand);background:#fff}.register-card{width:100%;box-shadow:none!important;border-left:0!important;border-right:0!important;border-bottom:0!important}.register-heading{justify-content:flex-start}}
</style>
