<template>
  <div class="page"><div class="heading"><div><h1>{{user.isStudent?'我的考勤':'考勤明细'}}</h1><p>按日期和场所查看签到、签退及考勤方式。</p></div><div class="heading-actions"><el-button v-if="user.isAdmin" type="primary" @click="openManual">管理员补签</el-button><el-button plain @click="exportCsv" :disabled="!rows.length">导出 CSV</el-button></div></div>
    <el-card v-loading="loading"><div class="filters"><el-date-picker v-model="range" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" clearable /><el-select v-if="!user.isStudent" v-model="officeId" placeholder="全部场所" clearable style="width:170px"><el-option v-for="office in offices" :key="office.id" :label="office.name" :value="office.id" /></el-select><el-button type="primary" @click="load">查询</el-button><span>共 {{rows.length}} 条记录</span></div>
      <el-table :data="rows" stripe empty-text="当前筛选条件下暂无记录"><el-table-column prop="workDate" label="日期" width="120" /><el-table-column v-if="!user.isStudent" prop="employeeName" label="员工" width="110" /><el-table-column v-if="!user.isStudent" prop="employeeNumber" label="工号" width="110" /><el-table-column prop="officeName" label="办公场所" min-width="145" /><el-table-column label="签到时间" width="175"><template #default="scope">{{dateTime(scope.row.checkInTime)}}</template></el-table-column><el-table-column label="签退时间" width="175"><template #default="scope">{{dateTime(scope.row.checkOutTime)}}</template></el-table-column><el-table-column label="工时" width="90"><template #default="scope">{{workTime(scope.row.workMinutes)}}</template></el-table-column><el-table-column label="方式" width="100"><template #default="scope">{{methodName(scope.row.method)}}</template></el-table-column><el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status==='LATE'?'warning':'success'">{{scope.row.status==='LATE'?'迟到':'正常'}}</el-tag></template></el-table-column><el-table-column label="识别相似度" width="120"><template #default="scope">{{scope.row.confidence==null?'—':Number(scope.row.confidence).toFixed(3)}}</template></el-table-column><el-table-column label="补签审计" min-width="230"><template #default="scope"><span v-if="scope.row.method==='MANUAL'">{{scope.row.manualOperator}} · {{scope.row.manualReason}}<small class="audit-time">{{dateTime(scope.row.manualCreatedAt)}} 操作</small></span><span v-else>—</span></template></el-table-column></el-table>
    </el-card>
    <el-dialog v-if="user.isAdmin" v-model="manualOpen" title="管理员补签" width="min(520px,96vw)">
      <el-form label-position="top" :model="manual">
        <el-form-item label="员工"><el-select v-model="manual.employeeId" filterable placeholder="选择员工" style="width:100%" @change="onEmployeeChange"><el-option v-for="employee in activeEmployees" :key="employee.id" :label="`${employee.realName}（${employee.employeeNumber}）`" :value="employee.id" /></el-select></el-form-item>
        <el-form-item label="办公场所"><el-select v-model="manual.officeId" placeholder="选择场所" style="width:100%"><el-option v-for="office in activeOffices" :key="office.id" :label="office.name" :value="office.id" /></el-select></el-form-item>
        <el-form-item label="实际签到时间"><el-date-picker v-model="manual.checkInTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择过去的时间" style="width:100%" :disabled-date="date => date.getTime() > Date.now()" /></el-form-item>
        <el-form-item label="补签原因"><el-input v-model="manual.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="填写原因，记录会保留操作人和操作时间" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="manualOpen=false">取消</el-button><el-button type="primary" :loading="manualSaving" @click="submitManual">确认补签</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getRecords, getOffices, getEmployees, manualCheckIn } from '@/api/enterprise'

const user=useUserStore(), rows=ref([]), offices=ref([]), range=ref(null), officeId=ref(null), loading=ref(false)
const employees=ref([]), manualOpen=ref(false), manualSaving=ref(false)
const manual=reactive({employeeId:null,officeId:null,checkInTime:'',reason:''})
const activeEmployees=computed(()=>employees.value.filter(item=>item.status===1))
const activeOffices=computed(()=>offices.value.filter(item=>item.status===1))
const dateTime=value=>value?String(value).replace('T',' ').slice(0,16):'—'
const workTime=minutes=>minutes==null?'—':`${Math.floor(minutes/60)}小时${String(minutes%60).padStart(2,'0')}分`
const methodName=value=>({FACE:'人脸',QR:'二维码',MANUAL:'管理员补签'}[value]||value||'—')
async function load() { loading.value=true; try { rows.value=await getRecords({ start:range.value?.[0],end:range.value?.[1],officeId:officeId.value||undefined }) } finally { loading.value=false } }
function onEmployeeChange(id) { manual.officeId=employees.value.find(item=>item.id===id)?.officeId||null }
async function openManual() { if(!employees.value.length) employees.value=await getEmployees(); Object.assign(manual,{employeeId:null,officeId:null,checkInTime:'',reason:''}); manualOpen.value=true }
async function submitManual() {
  if (!manual.employeeId || !manual.officeId || !manual.checkInTime || manual.reason.trim().length<4) return ElMessage.warning('请选择员工、场所、过去的签到时间，并填写至少 4 个字的原因')
  if (new Date(manual.checkInTime).getTime() >= Date.now()) return ElMessage.warning('补签时间必须早于现在')
  manualSaving.value=true
  try { await manualCheckIn({...manual,reason:manual.reason.trim()}); ElMessage.success('补签成功，操作已记录'); manualOpen.value=false; await load() }
  finally { manualSaving.value=false }
}
function exportCsv() { const lines=[['日期','员工','工号','场所','签到时间','签退时间','工时分钟','方式','状态','人脸相似度','补签操作人','补签原因','补签操作时间'],...rows.value.map(r=>[r.workDate,r.employeeName,r.employeeNumber,r.officeName,dateTime(r.checkInTime),dateTime(r.checkOutTime),r.workMinutes??'',methodName(r.method),r.status==='LATE'?'迟到':'正常',r.confidence??'',r.manualOperator??'',r.manualReason??'',r.manualCreatedAt?dateTime(r.manualCreatedAt):''])]; const csv='\ufeff'+lines.map(row=>row.map(v=>'"'+String(v??'').replaceAll('"','""')+'"').join(',')).join('\r\n'); const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'})); const a=document.createElement('a'); a.href=url; a.download=`联智考勤-${new Date().toISOString().slice(0,10)}.csv`; a.click(); setTimeout(()=>URL.revokeObjectURL(url),1000) }
onMounted(async()=>{if(!user.isStudent) offices.value=await getOffices();await load()})
</script>

<style scoped>
.page{display:grid;gap:20px}.heading{display:flex;justify-content:space-between;align-items:center;gap:15px}.heading-actions{display:flex;gap:8px;flex-wrap:wrap}.heading-actions .el-button{margin-left:0}.heading h1{font-size:24px;margin-bottom:8px}.heading p{font-size:13px;color:#8b9aaf}.filters{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:18px}.filters>span{margin-left:auto;font-size:12px;color:#95a3b6}.audit-time{display:block;color:#8b9aaf}@media(max-width:600px){.heading h1{font-size:21px}.heading{align-items:flex-start;flex-direction:column}.filters :deep(.el-date-editor){width:100%}.filters>span{margin-left:0}}
</style>
