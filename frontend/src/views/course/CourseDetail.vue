<template>
  <div class="course-detail" v-loading="loading">
    <div class="detail-hero">
      <el-button text class="back" @click="router.back()"><el-icon><ArrowLeft /></el-icon>返回课程列表</el-button>
      <div class="hero-content"><div class="course-mark"><el-icon><Reading /></el-icon></div><div><span class="course-code">{{ course.courseCode || '-' }}</span><h2>{{ course.courseName || '课程详情' }}</h2><p>{{ course.semester || '未设置学期' }} · {{ course.status === 0 ? '已停用' : '正常授课' }}</p></div></div>
    </div>

    <AttendanceSession v-if="userStore.isTeacher" :course-id="route.params.id" manager />
    <el-row :gutter="20" class="info-grid">
      <el-col :xs="24" :md="16"><el-card class="info-card"><template #header><b>课程信息</b></template>
        <div class="facts"><div><span>授课教师</span><strong>{{ course.teacherName || '-' }}</strong></div><div><span>上课地点</span><strong>{{ course.classroom || '-' }}</strong></div><div><span>上课日期</span><strong>{{ course.weekDayName || '-' }}</strong></div><div><span>上课时段</span><strong>{{ timeRange }}</strong></div><div><span>课程容量</span><strong>{{ course.maxStudents || '-' }} 人</strong></div><div><span>课程人数</span><strong>{{ course.enrolledCount ?? '-' }} 人</strong></div></div>
      </el-card></el-col>
      <el-col :xs="24" :md="8"><el-card class="notice-card"><template #header><b>考勤说明</b></template><div class="notice"><span>签到开放</span><p>上课前 15 分钟至下课后 30 分钟</p></div><div class="notice"><span>迟到判定</span><p>上课 10 分钟后签到计为迟到</p></div><div class="notice"><span>身份核验</span><p>需使用本人已注册人脸完成签到</p></div></el-card></el-col>
    </el-row>

    <el-card v-if="userStore.isTeacher" class="student-card"><template #header><div class="student-title"><div><b>上课学生信息</b><el-tag type="info">{{ students.length }} 人</el-tag></div><div class="add-student"><el-input v-model="studentNumber" placeholder="输入学号添加学生" @keyup.enter="addStudent"/><el-button type="primary" :loading="adding" @click="addStudent">添加学生</el-button></div></div></template><el-table :data="students" empty-text="暂无课程学生"><el-table-column prop="studentId" label="学号"/><el-table-column prop="realName" label="姓名"/><el-table-column label="班级"><template #default="{row}"><el-button link type="primary" @click="editClass(row)">{{row.className||'设置班级'}}</el-button></template></el-table-column><el-table-column prop="username" label="账号"/><el-table-column label="人脸"><template #default="{row}"><el-tag :type="row.faceRegistered?'success':'info'">{{row.faceRegistered?'已录入':'未录入'}}</el-tag></template></el-table-column><el-table-column label="状态"><template #default="{row}"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '停用' }}</el-tag></template></el-table-column></el-table></el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AttendanceSession from '@/components/AttendanceSession.vue'
import {setStudentClass} from '@/api/attendance'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Reading } from '@element-plus/icons-vue'
import { getCourseDetail, getCourseStudents, addCourseStudent } from '@/api/course'
import { useUserStore } from '@/stores/user'
const route=useRoute(), router=useRouter(), userStore=useUserStore(), loading=ref(false), course=ref({}), students=ref([]),studentNumber=ref(''),adding=ref(false)
async function editClass(row){let value;try{value=(await ElMessageBox.prompt('请输入该学生所属班级','设置班级',{inputValue:row.className||'',inputValidator:v=>!!v?.trim()||'请输入班级'})).value}catch{return}await setStudentClass(route.params.id,row.id,value);row.className=value;ElMessage.success('班级已更新')}
async function addStudent(){if(!studentNumber.value.trim())return ElMessage.warning('请输入学生学号');adding.value=true;try{await addCourseStudent(route.params.id,studentNumber.value.trim());studentNumber.value='';students.value=await getCourseStudents(route.params.id);ElMessage.success('学生已加入课程名单')}catch(e){if(!e.messageShown)ElMessage.error(e.message||'添加失败')}finally{adding.value=false}}
const timeRange=computed(()=>course.value.startTime&&course.value.endTime?`${course.value.startTime.slice(0,5)} - ${course.value.endTime.slice(0,5)}`:'-')
onMounted(async()=>{loading.value=true;try{course.value=await getCourseDetail(route.params.id);if(userStore.isTeacher)students.value=await getCourseStudents(route.params.id)}catch(e){ElMessage.error('课程详情加载失败')}finally{loading.value=false}})
</script>

<style scoped>
.course-detail {
  padding: 0;
}
.detail-hero{background:linear-gradient(120deg,#5b1b20,#a52c32);border-radius:16px;color:#fff;padding:22px 28px 30px;margin-bottom:20px}.back{color:#edd7d8;margin:0 0 18px -12px}.back:hover{color:#fff}.hero-content{display:flex;align-items:center;gap:18px}.course-mark{width:60px;height:60px;display:grid;place-items:center;background:rgba(255,255,255,.1);border:1px solid rgba(255,255,255,.16);border-radius:15px;font-size:28px}.course-code{font-size:12px;color:#f1c8ca;letter-spacing:1px}.hero-content h2{font-size:27px;margin:5px 0}.hero-content p{color:#edd7d8;margin:0;font-size:13px}.info-grid{margin-bottom:20px}.info-card,.notice-card{height:100%}.facts{display:grid;grid-template-columns:repeat(2,1fr);gap:0}.facts>div{padding:18px;border-bottom:1px solid var(--border)}.facts span{display:block;color:#988b8d;font-size:12px;margin-bottom:7px}.facts strong{font-size:15px;color:#44393b}.notice{padding:7px 0 17px}.notice+ .notice{border-top:1px solid var(--border);padding-top:17px}.notice span{font-weight:600;font-size:14px}.notice p{color:#706668;font-size:13px;line-height:1.6;margin:6px 0 0}.student-title{display:flex;align-items:center;justify-content:space-between;gap:16px}.student-title>div:first-child{display:flex;align-items:center;gap:10px}.add-student{display:flex;gap:8px}.add-student .el-input{width:210px}@media(max-width:768px){.facts{grid-template-columns:1fr}.info-grid .el-col+ .el-col{margin-top:20px}.detail-hero{padding:18px}.hero-content h2{font-size:22px}.student-title{align-items:flex-start;flex-direction:column}.add-student{width:100%}.add-student .el-input{width:auto;flex:1}}
</style>
