<template>
  <div class="course-list">
    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <div>
            <h3 class="card-title"><el-icon><Reading /></el-icon>{{ userStore.isStudent ? '课程中心' : userStore.isAdmin ? '课程信息管理' : '我的授课课程' }}</h3>
            <p class="card-subtitle">{{ userStore.isStudent ? '课程已由学校统一安排，可在这里查看课程信息' : userStore.isAdmin ? '仅展示当前正常使用的课程' : '创建课程，并维护自己课程的启停状态与学生名单' }}</p>
          </div>
          <el-button v-if="userStore.isTeacher" type="primary" @click="openEditor()">创建课程</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="courses" stripe empty-text="暂无课程">
        <el-table-column prop="courseName" label="课程名称" min-width="210">
          <template #default="{ row }"><div class="course-name"><strong>{{ row.courseName }}</strong><el-tag v-if="row.courseCode === 'TEST-UNLIMITED'" type="warning" size="small">测试</el-tag></div></template>
        </el-table-column>
        <el-table-column prop="courseCode" label="课程代码" width="135" />
        <el-table-column prop="teacherName" label="授课教师" width="110" />
        <el-table-column prop="classroom" label="教室" width="120" />
        <el-table-column label="上课时间" min-width="180"><template #default="{ row }">{{ scheduleText(row) }}</template></el-table-column>
        <el-table-column label="人数" width="100"><template #default="{ row }">{{ row.enrolledCount || 0 }}/{{ row.maxStudents || '-' }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '已停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" min-width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">查看</el-button>
            <template v-if="canManage(row)">
              <el-button type="primary" plain size="small" @click="openEditor(row)">编辑</el-button>
              <el-button :type="row.status === 1?'warning':'success'" plain size="small" @click="toggleStatus(row)">{{row.status===1?'停用':'恢复'}}</el-button>
              <el-button type="danger" plain size="small" @click="remove(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑课程' : '创建课程'" width="min(560px, 92vw)" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="课程名称" prop="courseName"><el-input v-model="form.courseName" /></el-form-item>
        <el-form-item label="课程代码" prop="courseCode"><el-input v-model="form.courseCode" /></el-form-item>
        <el-form-item label="授课教师"><el-input :model-value="userStore.userInfo?.realName" disabled /></el-form-item>
        <el-form-item label="教室"><el-input v-model="form.classroom" /></el-form-item>
        <el-form-item label="上课星期" prop="weekDay"><el-select v-model="form.weekDay" style="width:100%"><el-option v-for="day in weekOptions" :key="day.value" :label="day.label" :value="day.value" /></el-select></el-form-item>
        <el-form-item label="上课时间" required><div class="time-row"><el-time-select v-model="form.startTime" start="00:00" step="00:15" end="23:45" placeholder="开始" /><span>至</span><el-time-select v-model="form.endTime" start="00:15" step="00:15" end="23:59" placeholder="结束" /></div></el-form-item>
        <el-form-item label="学期"><el-input v-model="form.semester" placeholder="例如：2026-2027-1" /></el-form-item>
        <el-form-item label="课程容量"><el-input-number v-model="form.maxStudents" :min="1" :max="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editorVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Reading } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'
import { getCourseList, getEnrolledCourses, createCourse, updateCourse, deleteCourse, updateCourseStatus } from '@/api/course'

const userStore = useUserStore()
const router = useRouter()
const loading = ref(false), saving = ref(false), courses = ref([]), editorVisible = ref(false), editingId = ref(null), formRef = ref()
const weekOptions = ['星期一','星期二','星期三','星期四','星期五','星期六','星期日'].map((label,index)=>({label,value:index+1}))
const emptyForm = () => ({ courseName:'', courseCode:'', classroom:'', startTime:'08:00', endTime:'09:40', weekDay:1, semester:'2026-2027-1', maxStudents:50 })
const form = reactive(emptyForm())
const rules = { courseName:[{required:true,message:'请输入课程名称'}], courseCode:[{required:true,message:'请输入课程代码'}], weekDay:[{required:true,message:'请选择上课星期'}] }

const loadCourses = async () => {
  loading.value = true
  try {
    if (userStore.isStudent) {
      courses.value = await getEnrolledCourses()
      return
    }
    const query = { page:1, size:100 }
    if (userStore.isTeacher) query.teacherId = userStore.userInfo.id
    const allResult = await getCourseList(query)
    courses.value = allResult?.list || (Array.isArray(allResult) ? allResult : [])
  } catch (error) { if (!error.messageShown) ElMessage.error(error.message || '加载课程失败') }
  finally { loading.value = false }
}

const scheduleText = row => row.courseCode === 'TEST-UNLIMITED' ? '不限时间，可重复签到' : `${row.weekDayName || '-'} ${String(row.startTime||'').slice(0,5)}–${String(row.endTime||'').slice(0,5)}`
const canManage = () => userStore.isTeacher
const viewDetail = row => router.push(`/courses/${row.id}`)
const toggleStatus = async row => { try { const next=row.status===1?0:1;await ElMessageBox.confirm(next?'恢复后学生可重新查看课程并签到，确定继续吗？':'停用后学生将不能查看课程或签到，确定继续吗？',next?'恢复课程':'停用课程',{type:'warning'});await updateCourseStatus(row.id,next);ElMessage.success(next?'课程已恢复':'课程已停用');await loadCourses()}catch(error){if(error!=='cancel'&&error!=='close'&&!error.messageShown)ElMessage.error(error.message||'操作失败')} }
const remove = async row => { try { await ElMessageBox.confirm('永久删除会同时删除课程名单、签到及考勤数据，且无法恢复。确定删除吗？','永久删除课程',{type:'warning',confirmButtonText:'确定删除'});await deleteCourse(row.id);ElMessage.success('课程已删除');await loadCourses()}catch(error){if(error!=='cancel'&&error!=='close'&&!error.messageShown)ElMessage.error(error.message||'删除失败')} }

const openEditor = (row = null) => {
  editingId.value = row?.id || null
  Object.assign(form, emptyForm(), row ? { ...row, startTime:String(row.startTime).slice(0,5), endTime:String(row.endTime).slice(0,5) } : {})
  editorVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  if (!form.startTime || !form.endTime || form.startTime >= form.endTime) return ElMessage.warning('请设置正确的上课起止时间')
  saving.value = true
  try {
    const payload = { courseName:form.courseName, courseCode:form.courseCode, classroom:form.classroom, startTime:form.startTime, endTime:form.endTime, weekDay:form.weekDay, semester:form.semester, maxStudents:form.maxStudents }
    if (editingId.value) await updateCourse(editingId.value, payload)
    else await createCourse(payload)
    ElMessage.success(editingId.value ? '课程已更新' : '课程已创建')
    editorVisible.value = false
    await loadCourses()
  } catch(error) { if(!error.messageShown) ElMessage.error(error.message || '保存失败') }
  finally { saving.value = false }
}

onMounted(loadCourses)
</script>

<style scoped>
.main-card{min-height:420px}.card-header{display:flex;align-items:center;justify-content:space-between;gap:16px}.card-title{margin:0;display:flex;align-items:center;gap:8px;font-size:18px}.card-subtitle{margin:5px 0 0;color:#98a2b3;font-size:13px}.course-name{display:flex;align-items:center;gap:8px}.time-row{display:flex;align-items:center;gap:10px;width:100%}.time-row>*{flex:1}.time-row span{flex:0 0 auto;color:#98a2b3}@media(max-width:700px){.card-header{align-items:flex-start}.card-subtitle{max-width:220px}}
</style>
