<template>
  <div class="page" v-loading="loading"><div class="heading"><div><h1>员工管理</h1><p>维护员工账号、工号、办公场所和人脸录入状态。</p></div><el-button v-if="user.isAdmin" type="primary" @click="openCreate">新增员工</el-button></div>
    <el-card><div class="tools"><el-input v-model="keyword" clearable placeholder="搜索姓名、用户名或工号" style="max-width:300px" /><span>共 {{ filtered.length }} 位员工</span></div><el-table :data="filtered" stripe empty-text="暂无员工"><el-table-column prop="realName" label="姓名" min-width="110" /><el-table-column prop="employeeNumber" label="工号" min-width="115" /><el-table-column prop="username" label="登录名" min-width="115" /><el-table-column prop="officeName" label="办公场所" min-width="150"><template #default="scope">{{scope.row.officeName||'未分配'}}</template></el-table-column><el-table-column label="人脸" width="100"><template #default="scope"><el-tag :type="scope.row.faceRegistered?'success':'warning'">{{scope.row.faceRegistered?'已录入':'待录入'}}</el-tag></template></el-table-column><el-table-column label="状态" width="80"><template #default="scope"><el-tag :type="scope.row.status?'success':'info'">{{scope.row.status?'启用':'停用'}}</el-tag></template></el-table-column><el-table-column v-if="user.isAdmin" label="操作" width="210" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button v-if="scope.row.faceRegistered" link @click="clearFace(scope.row)">重置人脸</el-button><el-button link :type="scope.row.status?'danger':'success'" @click="toggleStatus(scope.row)">{{scope.row.status?'停用':'启用'}}</el-button></template></el-table-column></el-table></el-card>
    <el-dialog v-model="dialog" :title="editing ? '编辑员工' : '新增员工'" width="min(540px,96vw)"><el-form label-position="top"><div class="form-grid"><el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item><el-form-item label="工号"><el-input v-model="form.studentId" /></el-form-item></div><div class="form-grid"><el-form-item label="登录用户名"><el-input v-model="form.username" /></el-form-item><el-form-item :label="editing?'新密码（留空保持不变）':'初始密码'"><el-input v-model="form.password" type="password" show-password placeholder="8–20 位，含字母和数字" /></el-form-item></div><el-form-item label="办公场所"><el-select v-model="form.officeId" placeholder="选择所属场所" style="width:100%"><el-option v-for="office in activeOffices" :key="office.id" :label="`${office.companyName} · ${office.name}`" :value="office.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存员工</el-button></template></el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEmployees, getOffices, createEmployee, updateEmployee, assignOffice, setEmployeeStatus, resetFace } from '@/api/enterprise'

const user=useUserStore(), rows=ref([]), offices=ref([]), keyword=ref(''), loading=ref(false), saving=ref(false), dialog=ref(false), editing=ref(null)
const form=reactive({username:'',password:'',realName:'',studentId:'',officeId:null})
const activeOffices=computed(()=>offices.value.filter(x=>x.status===1))
const filtered=computed(()=>rows.value.filter(x=>[x.realName,x.username,x.employeeNumber].some(v=>String(v||'').includes(keyword.value.trim()))))
async function load() { loading.value=true; try { [rows.value,offices.value]=await Promise.all([getEmployees(),getOffices()]) } finally { loading.value=false } }
function openCreate() { editing.value=null; Object.assign(form,{username:'',password:'password123',realName:'',studentId:'',officeId:null}); dialog.value=true }
function openEdit(row) { editing.value=row; Object.assign(form,{username:row.username,password:'',realName:row.realName,studentId:row.employeeNumber||'',officeId:row.officeId}); dialog.value=true }
async function save() {
  if (!form.username || !form.realName || !form.studentId || !form.officeId || (!editing.value && !form.password)) return ElMessage.warning('请填写姓名、工号、用户名、初始密码和场所')
  if (form.password && !/^(?=.*[a-zA-Z])(?=.*\d)[a-zA-Z\d@$!%*?&]{8,20}$/.test(form.password)) return ElMessage.warning('密码需为 8–20 位，并同时包含字母和数字')
  saving.value=true
  try { const payload={username:form.username,realName:form.realName,studentId:form.studentId,password:form.password||null,status:editing.value?.status ?? 1}; const saved=editing.value?await updateEmployee(editing.value.id,payload):await createEmployee(payload); await assignOffice(saved.id,form.officeId); ElMessage.success('员工信息已保存'); dialog.value=false; await load() }
  finally { saving.value=false }
}
async function toggleStatus(row) { await ElMessageBox.confirm(`确定${row.status?'停用':'启用'} ${row.realName} 的账号吗？`,'确认操作'); await setEmployeeStatus(row.id,row.status?0:1); ElMessage.success('账号状态已更新'); await load() }
async function clearFace(row) { await ElMessageBox.confirm(`确定重置 ${row.realName} 的人脸资料吗？该员工需要重新使用摄像头录入。`,'确认重置'); await resetFace(row.id); ElMessage.success('人脸资料已重置'); await load() }
onMounted(load)
</script>

<style scoped>
.page{display:grid;gap:20px}.heading{display:flex;align-items:center;justify-content:space-between;gap:15px}.heading h1{font-size:24px;margin-bottom:8px}.heading p{font-size:13px;color:#8b9aaf}.tools{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px;gap:15px}.tools span{color:#93a0b1;font-size:12px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:15px}@media(max-width:600px){.heading h1{font-size:21px}.form-grid{grid-template-columns:1fr}.tools{align-items:flex-start;flex-direction:column}}
</style>
