<template>
  <el-container class="main-container">
    <el-header class="header">
      <div class="header-left">
        <div class="logo-section">
          <span class="header-emblem"><img :src="hnuLogo" alt="湖南大学校徽" /></span>
          <div class="brand-copy"><h2 class="title">湖南大学</h2><small>人脸考勤系统</small></div>
          <span class="product-tag">智慧校园</span>
        </div>
      </div>
      <div class="header-right">
        <div class="user-info">
          <el-avatar :size="40" :src="userStore.userInfo?.avatar">
            {{ userStore.userInfo?.realName?.charAt(0) || 'U' }}
          </el-avatar>
          <div class="user-details">
            <div class="user-name">{{ userStore.userInfo?.realName || '用户' }}</div>
            <div class="user-role">{{ getRoleText(userStore.userInfo?.role) }}</div>
          </div>
        </div>
        <el-dropdown @command="handleCommand" class="user-dropdown">
          <span class="el-dropdown-link">
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>个人信息
              </el-dropdown-item>
              <el-dropdown-item command="password">
                <el-icon><Lock /></el-icon>修改密码
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    
    <el-container class="content-shell">
      <el-aside class="sidebar">
        <el-menu
          :default-active="activeMenu"
          router
          class="sidebar-menu"
          active-text-color="#FFFFFF"
          background-color="#681F24"
          text-color="#bfcbd9"
        >
          <el-menu-item v-if="!userStore.isAdmin" index="/">
            <el-icon><HomeFilled /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>

          <template v-if="userStore.isStudent">
            <el-menu-item index="/courses">
              <el-icon><Reading /></el-icon>
              <span>我的课程</span>
            </el-menu-item>
            <el-menu-item index="/face/attendance">
              <el-icon><Camera /></el-icon>
              <span>人脸签到</span>
            </el-menu-item>
            <el-menu-item index="/face/register"><el-icon><UserFilled /></el-icon><span>人脸注册</span></el-menu-item>
            <el-menu-item index="/groups"><el-icon><UserFilled /></el-icon><span>我的小组</span></el-menu-item>
            <el-menu-item index="/attendance">
              <el-icon><Document /></el-icon>
              <span>考勤记录</span>
            </el-menu-item>
            <el-menu-item index="/attendance/leaves"><el-icon><Calendar /></el-icon><span>请假申请</span></el-menu-item>
          </template>

          <template v-else-if="userStore.isTeacher">
            <el-menu-item index="/courses">
              <el-icon><Reading /></el-icon>
              <span>课程管理</span>
            </el-menu-item>
            <el-menu-item index="/group-management"><el-icon><UserFilled /></el-icon><span>小组管理</span></el-menu-item>
            <el-menu-item index="/face/re-enroll-review"><el-icon><Camera /></el-icon><span>人脸重录审批</span></el-menu-item>
            <el-menu-item index="/attendance">
              <el-icon><Document /></el-icon>
              <span>考勤记录</span>
            </el-menu-item>
            <el-menu-item index="/attendance/statistics">
              <el-icon><TrendCharts /></el-icon>
              <span>考勤统计</span>
            </el-menu-item>
            <el-menu-item index="/attendance/leaves"><el-icon><Calendar /></el-icon><span>请假审批</span></el-menu-item>
          </template>

          <template v-else>
            <el-menu-item index="/users">
              <el-icon><UserFilled /></el-icon>
              <span>人员信息管理</span>
            </el-menu-item>
            <el-menu-item index="/courses">
              <el-icon><Reading /></el-icon>
              <span>课程信息管理</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>
      
      <el-main class="main-content">
        <div class="page-heading">
          <div><h1>{{ route.meta.title || '工作台' }}</h1><p>{{ pageDescription }}</p></div>
          <span class="today">{{ todayText }}</span>
        </div>
        <router-view />
      </el-main>
    </el-container>
    <nav class="mobile-nav" aria-label="主要导航">
      <router-link v-if="!userStore.isAdmin" to="/"><el-icon><HomeFilled /></el-icon><span>首页</span></router-link>
      <router-link v-if="userStore.isAdmin" to="/users"><el-icon><UserFilled /></el-icon><span>人员</span></router-link>
      <router-link to="/courses"><el-icon><Reading /></el-icon><span>{{userStore.isAdmin?'课程信息':'课程'}}</span></router-link>
      <template v-if="userStore.isStudent">
        <router-link to="/face/attendance" class="checkin-nav"><span class="checkin-icon"><el-icon><Camera /></el-icon></span><span>签到</span></router-link>
        <router-link to="/face/register"><el-icon><UserFilled /></el-icon><span>人脸</span></router-link>
        <router-link to="/attendance"><el-icon><Document /></el-icon><span>记录</span></router-link>
        <router-link to="/profile"><el-icon><User /></el-icon><span>我的</span></router-link>
      </template>
      <template v-else-if="userStore.isTeacher">
        <router-link to="/group-management"><el-icon><UserFilled /></el-icon><span>小组</span></router-link>
        <router-link to="/face/re-enroll-review"><el-icon><Camera /></el-icon><span>重录审批</span></router-link>
        <router-link to="/attendance"><el-icon><Document /></el-icon><span>记录</span></router-link>
        <router-link to="/attendance/statistics"><el-icon><TrendCharts /></el-icon><span>统计</span></router-link>
        <router-link to="/profile"><el-icon><User /></el-icon><span>我的</span></router-link>
      </template>
      <template v-else><router-link to="/profile"><el-icon><User /></el-icon><span>我的</span></router-link></template>
    </nav>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import hnuLogo from '@/assets/brand/hnu-logo.png'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  HomeFilled,
  Reading,
  Document,
  User,
  Camera,
  TrendCharts,
  ArrowDown,
  Lock,
  SwitchButton
  , Calendar, UserFilled
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const todayText = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const pageDescription = computed(() => ({ '/':'集中查看课程与考勤概况','/users':'统一维护学生与教师信息','/courses':userStore.isAdmin?'查看当前正常使用的课程':userStore.isTeacher?'创建和维护自己的课程':'查看学校统一安排的课程','/attendance':'查询每一位学生的每次考勤结果','/attendance/statistics':'从数据中了解出勤表现','/attendance/leaves':'提交与处理请假申请','/face/register':'首次录入本人面部信息，重录须经老师批准','/face/attendance':'通过人脸识别完成课程签到','/groups':userStore.isTeacher?'按课程分配小组并指定组长':'查看老师分配的小组' }[route.path] || '人脸考勤系统'))

// 获取用户信息
const loadUserInfo = async () => {
  if (userStore.token && !userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch (error) {
      console.error('获取用户信息失败:', error)
    }
  }
}

// 获取角色显示文本
const getRoleText = (role) => {
  const roles = {
    'STUDENT': '学生',
    'TEACHER': '教师',
    'ADMIN': '管理员'
  }
  return roles[role] || role
}

const handleCommand = async (command) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      userStore.logoutAction()
      await router.replace('/login')
      ElMessage.success('已退出登录')
    } catch (error) {
    }
  } else if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'password') {
    router.push('/password')
  }
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.main-container {
  height: 100vh;
  background-color: var(--canvas);
  overflow: hidden;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(105deg, var(--brand-deep), var(--brand));
  box-shadow: 0 2px 12px rgba(83,25,29,.2);
  padding: 0 28px;
  height: 70px;
  position: relative;
  z-index: 2;
}

.header-left {
  display: flex;
  align-items: center;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-emblem { position:relative; width:44px; height:44px; flex:0 0 44px; overflow:hidden; border-radius:50%; background:#fff; border:2px solid rgba(255,255,255,.72); box-shadow:0 3px 10px rgba(64,12,16,.18); }
.header-emblem img { position:absolute; height:44px; width:auto; max-width:none; left:0; top:0; }
.brand-copy { display:grid; gap:1px; }
.brand-copy small { color:rgba(255,255,255,.74); font-size:11px; letter-spacing:1.4px; }

.title {
  margin: 0;
  font-size: 22px;
  color: #fff;
  font-weight: 600;
}

.product-tag { font-size:12px; color:#fff7e5; background:rgba(198,161,91,.20); border:1px solid rgba(255,231,183,.40); padding:3px 8px; border-radius:999px; }

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-details {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 15px;
  color: #fff;
  font-weight: 500;
}

.user-role {
  font-size: 12px;
  color: rgba(255,255,255,.70);
}

.el-dropdown-link {
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 8px;
  color: #fff;
}

.sidebar {
  background: linear-gradient(180deg, #681f24, #4f181c);
  box-shadow: none;
  width: 240px;
  flex: 0 0 240px;
  height: calc(100vh - 70px);
  overflow: hidden;
}

.content-shell { min-width:0; height:calc(100vh - 70px); overflow:hidden; }

.sidebar-menu {
  border: none;
  height: 100%;
  overflow: hidden;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 240px;
}

.sidebar-menu .el-menu-item {
  color: #bfcbd9;
  height: 56px;
  line-height: 56px;
  font-size: 15px;
  margin: 6px 12px;
  width: calc(100% - 24px);
  border-radius: 9px;
  height: 48px;
  line-height: 48px;
}

.sidebar-menu .el-menu-item:hover,
.sidebar-menu .el-menu-item.is-active {
  color: #fff;
  background-color: rgba(255,255,255,.09);
}

.sidebar-menu .el-menu-item.is-active {
  background: linear-gradient(90deg, rgba(177,47,53,.9), rgba(177,47,53,.55));
  box-shadow: inset 3px 0 0 var(--brand-gold);
}

.sidebar-menu .el-menu-item .el-icon {
  margin-right: 10px;
}

.main-content {
  background-color: var(--canvas);
  padding: 26px 28px 40px;
  overflow-y: auto;
  overflow-x: hidden;
  min-width: 0;
}

.page-heading { display:flex; align-items:flex-end; justify-content:space-between; margin:0 0 22px; }
.page-heading h1 { margin:0; font-size:24px; line-height:1.35; letter-spacing:-.3px; color:var(--text); }
.page-heading p { margin:5px 0 0; color:#98a2b3; font-size:14px; }
.today { color:#667085; font-size:13px; background:#fff; border:1px solid var(--border); padding:8px 12px; border-radius:8px; }
.mobile-nav{display:none}
@media(max-width:900px){.sidebar{width:76px!important;flex-basis:76px}.sidebar-menu:not(.el-menu--collapse){width:76px}.sidebar-menu .el-menu-item{width:52px;margin:6px 12px;padding:0 14px!important}.sidebar-menu .el-menu-item span{display:none}.sidebar-menu .el-menu-item .el-icon{margin:0}.main-content{padding:20px 16px}.product-tag,.today,.user-details{display:none}.title{font-size:18px}}
@media(max-width:560px){.header{padding:0 14px}.title,.product-tag{display:none}.page-heading h1{font-size:21px}.main-content{padding:18px 12px 28px}}
@media(max-width:700px){
  .main-container{height:100%;min-height:100dvh}
  .header{height:58px;padding:0 16px}.logo-section .el-icon{width:28px}.header-right{gap:6px}.user-info .el-avatar{width:34px!important;height:34px!important}.user-dropdown{display:none}
  .content-shell{height:calc(100dvh - 58px);padding-bottom:calc(68px + env(safe-area-inset-bottom))}
  .sidebar{display:none!important}.main-content{padding:12px 14px calc(84px + env(safe-area-inset-bottom))}.page-heading{margin-bottom:12px}.page-heading p{font-size:12px}
  .mobile-nav{position:fixed;z-index:20;left:0;right:0;bottom:0;height:calc(64px + env(safe-area-inset-bottom));padding:6px 6px env(safe-area-inset-bottom);display:flex;align-items:center;justify-content:space-around;background:rgba(255,255,255,.97);border-top:1px solid #e7eaf0;box-shadow:0 -6px 24px rgba(16,24,40,.06)}
  .mobile-nav a{height:52px;min-width:54px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;color:#8b7e80;font-size:11px;border-radius:10px}.mobile-nav a>.el-icon{font-size:20px}.mobile-nav a.router-link-exact-active,.mobile-nav a.router-link-active{color:var(--brand)}
  .mobile-nav .checkin-nav{position:relative;color:var(--brand)}.checkin-icon{width:42px;height:42px;margin-top:-22px;border-radius:15px;background:var(--brand);color:#fff;display:grid;place-items:center;box-shadow:0 8px 18px rgba(177,47,53,.30);border:4px solid #fff}.checkin-icon .el-icon{font-size:20px}
}
</style>
