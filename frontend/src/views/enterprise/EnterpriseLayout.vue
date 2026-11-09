<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">联</span><div><strong>联智</strong><small>企业智能考勤</small></div></div>
      <p class="nav-caption">工作空间</p>
      <router-link v-for="item in links" :key="item.path" :to="item.path" class="nav-link" :class="{ active: active(item.path) }">
        <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
      </router-link>
      <div class="side-bottom"><span class="status-dot"></span> 系统运行中 <small>v1.0 实训版</small></div>
    </aside>
    <div class="main-column">
      <header class="topbar"><div><b>{{ route.meta.title || '工作台' }}</b><span>{{ today }}</span></div>
        <div class="account"><el-avatar :size="34">{{ user.userInfo?.realName?.slice(0,1) || '联' }}</el-avatar><span>{{ user.userInfo?.realName || '用户' }}<small>{{ roleText }}</small></span>
          <el-dropdown @command="handleCommand"><el-button text><el-icon><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="password">修改密码</el-dropdown-item><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </div>
      </header>
      <main class="content"><router-view /></main>
      <nav class="mobile-nav"><router-link v-for="item in mobileLinks" :key="item.path" :to="item.path" :class="{ active: active(item.path) }"><el-icon><component :is="item.icon" /></el-icon><span>{{ item.short || item.label }}</span></router-link></nav>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ArrowDown, DataBoard, Location, User, Tickets, Camera, Grid, Document, ChatDotRound } from '@element-plus/icons-vue'

const user = useUserStore(), route = useRoute(), router = useRouter()
const today = new Intl.DateTimeFormat('zh-CN', { year:'numeric', month:'long', day:'numeric', weekday:'long' }).format(new Date())
const roleText = computed(() => user.isAdmin ? '系统管理员' : user.isTeacher ? '部门主管' : '员工')
const links = computed(() => user.isStudent ? [
  { path:'/', label:'我的工作台', short:'首页', icon:DataBoard },
  { path:'/face', label:'人脸考勤', short:'人脸', icon:Camera },
  { path:'/qr-checkin', label:'扫码签到', short:'扫码', icon:Grid },
  { path:'/records', label:'我的考勤', short:'记录', icon:Document },
  { path:'/agent', label:'考勤 Agent', short:'问答', icon:ChatDotRound }
] : [
  { path:'/', label:'考勤总览', short:'总览', icon:DataBoard },
  { path:'/offices', label:'办公场所', short:'场所', icon:Location },
  { path:'/employees', label:'员工管理', short:'员工', icon:User },
  { path:'/records', label:'考勤明细', short:'记录', icon:Tickets },
  { path:'/agent', label:'考勤 Agent', short:'问答', icon:ChatDotRound }
])
const mobileLinks = computed(() => links.value.filter(item => item.path !== '/employees').slice(0, 5))
const active = path => path === '/' ? route.path === '/' : route.path.startsWith(path)
const handleCommand = command => {
  if (command === 'password') return router.push('/password')
  if (command === 'logout') { user.logoutAction(); router.replace('/login') }
}
</script>

<style scoped>
.shell{height:100vh;display:flex;background:#f3f6fb;color:#17243c}.sidebar{width:238px;background:#10243f;color:#d5e2f4;display:flex;flex-direction:column;padding:27px 14px;flex:none}.brand{display:flex;align-items:center;gap:12px;padding:0 14px 34px;color:#fff}.brand-mark{width:40px;height:40px;display:grid;place-items:center;background:linear-gradient(135deg,#32a7ff,#3561da);border-radius:12px;font-size:19px;font-weight:800}.brand strong,.brand small,.account span,.account small{display:block}.brand strong{font-size:21px;letter-spacing:3px}.brand small{font-size:11px;letter-spacing:1px;color:#94b1d3}.nav-caption{font-size:11px;color:#7e9abc;padding:0 17px 12px;letter-spacing:2px}.nav-link{height:46px;display:flex;align-items:center;gap:13px;padding:0 15px;margin:3px 0;border-radius:10px;color:#bfd0e8;font-size:14px}.nav-link .el-icon{font-size:18px}.nav-link:hover,.nav-link.active{background:#1d3b64;color:#fff}.nav-link.active{box-shadow:inset 3px 0 #45b8ff}.side-bottom{margin-top:auto;padding:16px;color:#9fb5d0;font-size:12px}.side-bottom small{display:block;padding:7px 0 0 17px;color:#6683a6}.status-dot{display:inline-block;width:7px;height:7px;background:#39c891;border-radius:50%;margin-right:6px}.main-column{min-width:0;flex:1;display:flex;flex-direction:column}.topbar{height:72px;background:#fff;border-bottom:1px solid #e4eaf3;display:flex;align-items:center;justify-content:space-between;padding:0 30px}.topbar>div:first-child{display:flex;align-items:center;gap:20px}.topbar b{font-size:17px}.topbar span{color:#8492a9;font-size:12px}.account{display:flex;align-items:center;gap:10px}.account .el-avatar{background:#dbe9ff;color:#2d66c8;font-weight:700}.account span{font-size:13px;color:#1f2d43}.account small{color:#8c9bb0;font-size:11px;margin-top:3px}.content{flex:1;overflow:auto;padding:28px 32px 45px}.mobile-nav{display:none}
@media(max-width:800px){.sidebar{display:none}.topbar{height:60px;padding:0 16px}.topbar>div:first-child{display:block}.topbar>div:first-child span{display:none}.content{padding:18px 14px 94px}.mobile-nav{position:fixed;bottom:0;left:0;right:0;height:64px;background:#fff;border-top:1px solid #e6ebf4;display:flex;z-index:20;padding-bottom:env(safe-area-inset-bottom)}.mobile-nav a{flex:1;display:grid;justify-items:center;align-content:center;gap:4px;color:#8190a6;font-size:11px}.mobile-nav a .el-icon{font-size:20px}.mobile-nav a.active{color:#266de1}.account span{display:none}}
</style>
