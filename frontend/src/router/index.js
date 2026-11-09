import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path:'/login', component:() => import('@/views/auth/Login.vue'), meta:{ public:true, title:'登录' } },
  { path:'/', component:() => import('@/views/enterprise/EnterpriseLayout.vue'), meta:{ requiresAuth:true }, children:[
    { path:'', component:() => import('@/views/enterprise/Overview.vue'), meta:{ title:'工作台' } },
    { path:'offices', component:() => import('@/views/enterprise/Offices.vue'), meta:{ title:'办公场所', roles:['ADMIN','TEACHER'] } },
    { path:'employees', component:() => import('@/views/enterprise/Employees.vue'), meta:{ title:'员工管理', roles:['ADMIN','TEACHER'] } },
    { path:'qr-checkin', component:() => import('@/views/enterprise/QrCheckIn.vue'), meta:{ title:'二维码签到', roles:['STUDENT'] } },
    { path:'face', component:() => import('@/views/enterprise/FaceCheckIn.vue'), meta:{ title:'人脸考勤', roles:['STUDENT'] } },
    { path:'records', component:() => import('@/views/enterprise/Records.vue'), meta:{ title:'考勤明细' } },
    { path:'agent', component:() => import('@/views/enterprise/AttendanceAgent.vue'), meta:{ title:'考勤 Agent' } },
    { path:'password', component:() => import('@/views/user/ChangePassword.vue'), meta:{ title:'修改密码' } }
  ] },
  { path:'/:pathMatch(.*)*', redirect:'/' }
]

const router = createRouter({ history:createWebHistory(), routes })
router.beforeEach(async to => {
  const user = useUserStore()
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) { user.clearSession(); return { path:'/login', query:{ redirect:to.fullPath } } }
  if (!token) return true
  if (!user.userInfo) {
    try { await user.fetchUserInfo({ silent:true, skipAuthRedirect:true }) }
    catch { user.clearSession(); return to.path === '/login' ? true : { path:'/login', query:{ redirect:to.fullPath } } }
  }
  if (to.path === '/login') return '/'
  if (to.meta.roles && !to.meta.roles.includes(user.userInfo?.role)) return '/'
  return true
})

export default router
