<template>
  <div class="login-page"><div class="intro"><div class="logo">联</div><p class="eyebrow">LIANZHI · WORKFORCE INTELLIGENCE</p><h1>让每一次考勤<br>准确、便捷、可追溯</h1><p class="subtitle">办公场所管理 · 动态二维码 · 真实人脸识别 · 实时数据看板</p><div class="visual"><span>01 <b>员工与场所</b></span><span>02 <b>智能签到</b></span><span>03 <b>数据分析</b></span></div></div>
    <div class="login-panel"><div class="mobile-brand">联智 · 企业智能考勤</div><h2>欢迎回来</h2><p>登录联智工作空间，开始今天的工作。</p>
      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="submit">
        <el-form-item prop="username"><el-input v-model="form.username" size="large" placeholder="用户名" prefix-icon="User" autocomplete="username" /></el-form-item>
        <el-form-item prop="password"><el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" prefix-icon="Lock" autocomplete="current-password" @keyup.enter="submit" /></el-form-item>
        <el-button class="submit" type="primary" size="large" :loading="loading" @click="submit">登录系统</el-button>
      </el-form>
      <el-collapse class="settings"><el-collapse-item title="服务器地址（一般留空）"><el-input v-model="serverUrl" placeholder="本机与手机浏览器访问此网页时留空" /><p>手机通过电脑的 5173 端口打开此网页时请留空，网页会自动连接后端。仅独立 App 或另行部署时才需填写；手机摄像头人脸识别需要 HTTPS。</p><el-button plain @click="saveServer">保存地址</el-button></el-collapse-item></el-collapse>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getConfiguredApiBaseUrl, setApiBaseUrl } from '@/utils/request'

const router = useRouter(), user = useUserStore(), formRef = ref(), loading = ref(false)
const form = reactive({ username:'', password:'' })
const rules = { username:[{ required:true, message:'请输入用户名', trigger:'blur' }], password:[{ required:true, message:'请输入密码', trigger:'blur' }] }
const serverUrl = ref(getConfiguredApiBaseUrl() === '/api' ? '' : getConfiguredApiBaseUrl())
function saveServer() {
  const value = serverUrl.value.trim()
  if (value && !/^https?:\/\//i.test(value)) { ElMessage.warning('服务器地址应以 http:// 或 https:// 开头'); return false }
  setApiBaseUrl(value); ElMessage.success(value ? '服务器地址已保存' : '已使用默认地址'); return true
}
async function submit() {
  const value = serverUrl.value.trim()
  if (value && !/^https?:\/\//i.test(value)) { ElMessage.warning('服务器地址格式不正确'); return }
  setApiBaseUrl(value)
  if (!await formRef.value.validate().catch(() => false)) return
  loading.value = true
  try { await user.loginAction(form.username, form.password); ElMessage.success('登录成功'); await router.replace(typeof router.currentRoute.value.query.redirect === 'string' ? router.currentRoute.value.query.redirect : '/') }
  catch (error) { if (!error.messageShown) ElMessage.error(error.message || '登录失败') }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-page{height:100vh;display:grid;grid-template-columns:1.12fr .88fr;background:#f7f9fd}.intro{padding:clamp(35px,6vw,100px);background:radial-gradient(circle at 75% 25%,#214f92 0,transparent 38%),linear-gradient(135deg,#0a1f3d,#123962);color:#fff;display:flex;flex-direction:column;justify-content:center}.logo{width:54px;height:54px;border-radius:15px;background:linear-gradient(135deg,#49b8ff,#4b70ed);display:grid;place-items:center;font-size:24px;font-weight:700;margin-bottom:48px}.eyebrow{letter-spacing:3px;color:#9dc2e9;font-size:11px;font-weight:700}.intro h1{font-size:clamp(33px,3.6vw,58px);line-height:1.28;margin:18px 0 23px}.subtitle{color:#b7cce4;font-size:16px;line-height:1.8}.visual{display:flex;gap:15px;margin-top:90px}.visual span{min-width:125px;border-top:1px solid #456b96;padding-top:16px;color:#73b6ff;font-size:12px}.visual b{display:block;color:#dcecff;font-size:14px;margin-top:8px;font-weight:500}.login-panel{width:min(420px,calc(100% - 44px));align-self:center;justify-self:center}.mobile-brand{display:none}.login-panel h2{font-size:32px;color:#162942;margin-bottom:10px}.login-panel>p{color:#8492a7;margin-bottom:32px}.login-panel .el-form-item{margin-bottom:22px}.submit{width:100%;height:46px;margin-top:6px}.settings{margin-top:24px}.settings p{line-height:1.6;color:#8995a7;font-size:12px;margin:11px 0}.settings :deep(.el-collapse-item__header){color:#7789a2;font-size:12px}
@media(max-width:760px){.login-page{display:flex;align-items:center;justify-content:center;padding:20px;background:linear-gradient(160deg,#0e2e58 0,#174877 34%,#f5f8fc 34%)}.intro{display:none}.login-panel{background:#fff;border-radius:19px;padding:28px 22px;box-shadow:0 22px 55px rgba(13,36,70,.18)}.mobile-brand{display:block;color:#2e68c7;font-weight:700;margin-bottom:28px}.login-panel h2{font-size:26px}}
</style>
