<template>
  <div class="attendance-record">
    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <h3 class="card-title">
              <el-icon><Document /></el-icon>
              考勤记录
            </h3>
          </div>
          <div class="header-right">
            <el-button type="primary" @click="loadRecords">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading" class="table-container">
        <el-table :data="attendanceRecords" style="width: 100%" stripe>
          <el-table-column label="课程" min-width="180">
            <template #default="scope"><div class="course-cell"><strong>{{ scope.row.courseName }}</strong><span>{{ scope.row.courseCode || '-' }}</span></div></template>
          </el-table-column>
          <el-table-column prop="studentName" label="学生姓名" width="120"></el-table-column>
          <el-table-column v-if="userStore.isTeacher" prop="studentNumber" label="学号" width="120"></el-table-column>
          <el-table-column label="应上课日期" width="150">
            <template #default="scope"><span class="date-text">{{ formatDate(scope.row.attendanceDate) }}</span></template>
          </el-table-column>
          <el-table-column label="课程时段" width="150">
            <template #default="scope">{{ formatSchedule(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="考勤状态" width="100">
            <template #default="scope">
              <el-tag :type="getStatusType(scope.row.status)">
                {{ getStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="实际签到时间" min-width="180">
            <template #default="scope"><span v-if="scope.row.checkInTime">{{ formatTime(scope.row.checkInTime) }}</span><span v-else class="no-check-in">{{ scope.row.status === 'ABSENT' ? '未签到' : '-' }}</span></template>
          </el-table-column>
          <el-table-column v-if="userStore.isTeacher" label="异常调整" width="120" fixed="right"><template #default="scope"><el-dropdown @command="status=>changeStatus(scope.row,status)"><el-button size="small" type="primary" plain>修改状态</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="PRESENT">正常</el-dropdown-item><el-dropdown-item command="LATE">迟到</el-dropdown-item><el-dropdown-item command="ABSENT">缺勤</el-dropdown-item><el-dropdown-item command="LEAVE">请假</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column>
        </el-table>
      </div>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="loadRecords"
        @current-change="loadRecords"
      ></el-pagination>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Refresh } from '@element-plus/icons-vue'
import { getAttendanceRecords, updateAttendanceStatus } from '@/api/attendance'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'

const loading = ref(false)
const userStore = useUserStore()
const attendanceRecords = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const formatTime = (time) => time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
const formatDate = (date) => date ? dayjs(date).format('YYYY-MM-DD') : '-'
const shortTime = (time) => time ? String(time).slice(0, 5) : ''
const formatSchedule = (row) => row.scheduledStartTime && row.scheduledEndTime
  ? `${shortTime(row.scheduledStartTime)} - ${shortTime(row.scheduledEndTime)}` : '-'

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    'PRESENT': 'success',
    'LATE': 'warning',
    'ABSENT': 'danger',
    'LEAVE': 'info'
  }
  return types[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    'PRESENT': '正常',
    'LATE': '迟到',
    'ABSENT': '缺勤',
    'LEAVE': '请假'
  }
  return texts[status] || status
}

const changeStatus = async (row, status) => {
  if (status === row.status) return
  try {
    await ElMessageBox.confirm(`确定将 ${row.studentName} 的考勤改为“${getStatusText(status)}”吗？此操作仅用于处理特殊情况。`, '调整考勤状态', { type:'warning' })
    await updateAttendanceStatus(row.id, status)
    ElMessage.success('考勤状态已更新')
    await loadRecords()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && !error.messageShown) ElMessage.error(error.message || '修改失败')
  }
}

// 加载考勤记录
const loadRecords = async () => {
  loading.value = true
  try {
    const res = await getAttendanceRecords({
      page: pageNum.value,
      size: pageSize.value
    })
    
    if (res && Array.isArray(res.list)) {
      attendanceRecords.value = res.list
      total.value = res.total || 0
    } else if (Array.isArray(res)) {
      attendanceRecords.value = res
      total.value = res.length
    } else {
      attendanceRecords.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('加载考勤记录失败:', error)
    attendanceRecords.value = []
    total.value = 0
    ElMessage.error('考勤记录加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadRecords()
})
</script>

<style scoped>
.attendance-record {
  padding: 0;
}

.main-card {
  min-height: 420px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-container {
  padding: 10px 0;
}
.course-cell{display:flex;flex-direction:column;gap:4px}.course-cell strong{color:#344054;font-weight:600}.course-cell span{font-size:12px;color:#98a2b3}.date-text{font-variant-numeric:tabular-nums}.no-check-in{color:#f04438;font-weight:500}
</style>
