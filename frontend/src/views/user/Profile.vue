<template>
  <div class="profile">
    <el-card>
      <template #header>
        <span class="card-header">个人信息</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户名">{{ userStore.userInfo?.username }}</el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ userStore.userInfo?.realName }}</el-descriptions-item>
        <el-descriptions-item label="学号" v-if="userStore.userInfo?.studentId">{{ userStore.userInfo?.studentId }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ getRoleName(userStore.userInfo?.role) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="userStore.userInfo?.status === 1 ? 'success' : 'danger'">
            {{ userStore.userInfo?.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()

const getRoleName = (role) => {
  const roles = {
    'STUDENT': '学生',
    'TEACHER': '教师',
    'ADMIN': '管理员'
  }
  return roles[role] || role
}
</script>

<style scoped>
.profile {
  padding: 10px;
}

.card-header {
  font-weight: bold;
  font-size: 16px;
}

</style>
