<template>
  <div class="user-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <div><h3>人员信息管理</h3><p>统一维护学生和教师账号，其他业务由对应角色处理</p></div>
          <div class="header-actions">
            <el-button @click="openImport"><el-icon><UploadFilled /></el-icon>批量导入</el-button>
            <el-button type="primary" @click="openEditor()"><el-icon><Plus /></el-icon>添加人员</el-button>
          </div>
        </div>
      </template>

      <div class="filters">
        <el-select v-model="filters.role" clearable placeholder="全部角色" @change="load"><el-option label="学生" value="STUDENT"/><el-option label="教师" value="TEACHER"/></el-select>
        <el-input v-model="filters.keyword" clearable placeholder="用户名、姓名、学号或工号" @keyup.enter="load" @clear="load"/>
        <el-button @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无人员">
        <el-table-column prop="username" label="用户名" min-width="120"/>
        <el-table-column prop="realName" label="姓名" min-width="100"/>
        <el-table-column label="角色" width="90"><template #default="{row}"><el-tag :type="row.role==='TEACHER'?'warning':'success'">{{roleText(row.role)}}</el-tag></template></el-table-column>
        <el-table-column prop="studentId" label="学号 / 工号" min-width="130"><template #default="{row}">{{row.studentId||'—'}}</template></el-table-column>
        <el-table-column prop="className" label="班级" min-width="110"><template #default="{row}">{{row.className||'—'}}</template></el-table-column>
        <el-table-column label="状态" width="85"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{row.status===1?'正常':'停用'}}</el-tag></template></el-table-column>
        <el-table-column label="操作" fixed="right" width="250"><template #default="{row}">
          <el-button link @click="detail=row">查看</el-button>
          <el-button link type="primary" @click="openEditor(row)">修改</el-button>
          <el-button link :type="row.status===1?'warning':'success'" @click="toggleStatus(row)">{{row.status===1?'停用':'启用'}}</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
      <el-pagination v-if="total" v-model:current-page="page" :page-size="10" :total="total" layout="total, prev, pager, next" @current-change="load"/>
    </el-card>

    <el-dialog v-model="editorVisible" :title="editingId?'修改人员信息':'添加人员'" width="min(560px,94vw)" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="95px">
        <el-form-item label="角色" prop="role"><el-radio-group v-model="form.role"><el-radio-button value="STUDENT">学生</el-radio-button><el-radio-button value="TEACHER">教师</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" placeholder="4–20位字母、数字或下划线"/></el-form-item>
        <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName"/></el-form-item>
        <el-form-item :label="form.role==='STUDENT'?'学号':'工号'" prop="studentId"><el-input v-model="form.studentId" :placeholder="form.role==='STUDENT'?'学生必须填写学号':'教师工号可选'"/></el-form-item>
        <el-form-item v-if="form.role==='STUDENT'" label="班级"><el-input v-model="form.className" placeholder="例如：计算机2401班"/></el-form-item>
        <el-form-item :label="editingId?'重置密码':'初始密码'" prop="password"><el-input v-model="form.password" type="password" show-password :placeholder="editingId?'不修改请留空':'8–20位，包含字母和数字'"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="editorVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog :model-value="!!detail" title="人员详情" width="min(500px,94vw)" @close="detail=null">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="用户名">{{detail.username}}</el-descriptions-item><el-descriptions-item label="姓名">{{detail.realName}}</el-descriptions-item>
        <el-descriptions-item label="角色">{{roleText(detail.role)}}</el-descriptions-item><el-descriptions-item label="学号 / 工号">{{detail.studentId||'—'}}</el-descriptions-item>
        <el-descriptions-item label="班级">{{detail.className||'—'}}</el-descriptions-item><el-descriptions-item label="状态">{{detail.status===1?'正常':'停用'}}</el-descriptions-item>
        <el-descriptions-item label="人脸状态" v-if="detail.role==='STUDENT'">{{detail.faceRegistered?'已录入':'未录入'}}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="importVisible" title="Excel批量导入名单" width="min(640px,94vw)" @closed="resetImport">
      <el-form label-width="88px">
        <el-form-item label="名单类型">
          <el-radio-group v-model="importRole" @change="onImportRoleChange">
            <el-radio-button value="STUDENT">学生名单</el-radio-button>
            <el-radio-button value="TEACHER">教师名单</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="填写要求">
          <div class="import-hint">
            <p>Excel 第一行须为表头，仅支持 .xlsx 格式，单次最多2000条；密码列留空时默认 password123。</p>
            <p v-if="importRole==='STUDENT'">学生名单列：用户名（学号）、姓名、学号、班级、密码</p>
            <p v-else>教师名单列：用户名（工号）、姓名、工号、密码</p>
          </div>
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload drag :auto-upload="false" :show-file-list="false" accept=".xlsx" :on-change="onFilePicked" class="import-upload">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">将 .xlsx 文件拖到此处，或<em>点击选择</em></div>
          </el-upload>
          <div v-if="importFile" class="picked-file">
            已选择：{{importFile.name}}（{{(importFile.size/1024).toFixed(1)}} KB）
            <el-button link type="danger" @click="importFile=null">移除</el-button>
          </div>
        </el-form-item>
      </el-form>

      <div v-if="importResult" class="import-result">
        <el-alert :type="importResult.failCount?'warning':'success'" :closable="false" show-icon>
          <template #title>
            <template v-if="importResult.failCount">共{{importResult.totalCount}}条，{{importResult.successCount}}条校验通过，{{importResult.failCount}}条失败；存在失败记录，整批未导入（事务已回滚），请按下表修正后重新上传</template>
            <template v-else>成功导入 {{importResult.successCount}} 条{{importRole==='STUDENT'?'学生':'教师'}}记录，初始密码为Excel中填写的密码，留空则为默认 password123</template>
          </template>
        </el-alert>
        <el-table v-if="importResult.failures?.length" :data="importResult.failures" max-height="260" size="small" stripe class="fail-table">
          <el-table-column prop="row" label="Excel行号" width="90"/>
          <el-table-column prop="username" label="用户名" min-width="110"><template #default="{row}">{{row.username||'—'}}</template></el-table-column>
          <el-table-column prop="realName" label="姓名" width="90"><template #default="{row}">{{row.realName||'—'}}</template></el-table-column>
          <el-table-column prop="reason" label="失败原因" min-width="230"/>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="importVisible=false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, UploadFilled } from '@element-plus/icons-vue'
import { getUsers, createUser, updateManagedUser, updateUserStatus, deleteUser, importUsers } from '@/api/user'
const rows=ref([]),total=ref(0),page=ref(1),loading=ref(false),saving=ref(false),editorVisible=ref(false),editingId=ref(null),detail=ref(null),formRef=ref()
const filters=reactive({role:'',keyword:''})
const empty=()=>({role:'STUDENT',username:'',realName:'',studentId:'',className:'',password:'password123',status:1})
const form=reactive(empty())
const rules={role:[{required:true,message:'请选择角色'}],username:[{required:true,message:'请输入用户名'},{pattern:/^[a-zA-Z0-9_]{4,20}$/,message:'用户名须为4–20位字母、数字或下划线'}],realName:[{required:true,message:'请输入姓名'}],studentId:[{validator:(_,v,cb)=>form.role==='STUDENT'&&!v?.trim()?cb(new Error('学生必须填写学号')):cb()}],password:[{validator:(_,v,cb)=>{if(!editingId.value&&!v)return cb(new Error('请输入初始密码'));if(v&&!/^(?=.*[a-zA-Z])(?=.*\d)[a-zA-Z\d@$!%*?&]{8,20}$/.test(v))return cb(new Error('密码须为8–20位，并包含字母和数字'));cb()}}]}
const roleText=role=>role==='TEACHER'?'教师':'学生'
async function load(){loading.value=true;try{const result=await getUsers({page:page.value,size:10,role:filters.role||undefined,keyword:filters.keyword||undefined});rows.value=(result?.list||[]).filter(item=>item.role!=='ADMIN');total.value=result?.total||0}catch(e){if(!e.messageShown)ElMessage.error(e.message||'加载人员失败')}finally{loading.value=false}}
function openEditor(row=null){editingId.value=row?.id||null;Object.assign(form,empty(),row?{role:row.role,username:row.username,realName:row.realName,studentId:row.studentId||'',className:row.className||'',password:'',status:row.status}:{});editorVisible.value=true}
async function save(){if(!await formRef.value.validate().catch(()=>false))return;saving.value=true;try{const payload={role:form.role,username:form.username.trim(),realName:form.realName.trim(),studentId:form.studentId?.trim()||null,className:form.role==='STUDENT'?(form.className?.trim()||null):null,password:form.password||'',status:form.status};const saved=editingId.value?await updateManagedUser(editingId.value,payload):await createUser(payload);if(editingId.value&&saved){const index=rows.value.findIndex(item=>item.id===editingId.value);if(index>=0)rows.value.splice(index,1,saved)}ElMessage.success(editingId.value?'人员信息已更新':'人员添加成功');editorVisible.value=false;await load()}catch(e){if(!e.messageShown)ElMessage.error(e.message||'保存失败')}finally{saving.value=false}}
async function toggleStatus(row){try{await ElMessageBox.confirm(`确定${row.status===1?'停用':'启用'} ${row.realName} 的账号吗？`,'账号状态',{type:'warning'});await updateUserStatus(row.id,row.status===1?0:1);ElMessage.success('账号状态已更新');await load()}catch(e){if(e!=='cancel'&&e!=='close'&&!e.messageShown)ElMessage.error(e.message||'操作失败')}}
async function remove(row){try{await ElMessageBox.confirm(`删除 ${row.realName} 后无法恢复，确定继续吗？`,'删除人员',{type:'warning',confirmButtonText:'确定删除'});await deleteUser(row.id);ElMessage.success('人员已删除');await load()}catch(e){if(e!=='cancel'&&e!=='close'&&!e.messageShown)ElMessage.error(e.message||'有关联业务数据时请改为停用账号')}}

const importVisible=ref(false),importing=ref(false),importRole=ref('STUDENT'),importFile=ref(null),importResult=ref(null)
function openImport(){importResult.value=null;importFile.value=null;importVisible.value=true}
function resetImport(){importFile.value=null;importResult.value=null;importing.value=false}
function onImportRoleChange(){importFile.value=null;importResult.value=null}
function onFilePicked(uploadFile){
  const file=uploadFile?.raw
  if(!file)return
  const name=file.name.toLowerCase()
  if(!name.endsWith('.xlsx')){ElMessage.error('仅支持 .xlsx 格式的Excel文件');return}
  if(file.size>10*1024*1024){ElMessage.error('文件大小不能超过10MB，请拆分名单后上传');return}
  importFile.value=file
  importResult.value=null
}
async function submitImport(){
  if(!importFile.value)return
  importing.value=true
  try{
    const result=await importUsers(importRole.value,importFile.value)
    importResult.value=result
    if(result?.failCount){ElMessage.warning(`校验未通过：${result.failCount}条失败，整批未导入`)}
    else{
      ElMessage.success(`成功导入${result?.successCount||0}条记录`)
      importVisible.value=false
      page.value=1
      await load()
    }
  }catch(e){if(!e.messageShown)ElMessage.error(e.message||'导入失败，请检查文件格式')}
  finally{importing.value=false}
}
onMounted(load)
</script>

<style scoped>.card-header{display:flex;align-items:center;justify-content:space-between;gap:16px}.card-header h3{margin:0}.card-header p{margin:6px 0 0;color:#988b8d;font-size:13px}.header-actions{display:flex;gap:10px;flex-shrink:0}.filters{display:flex;gap:10px;margin-bottom:18px}.filters .el-select{width:150px}.filters .el-input{max-width:300px}.el-pagination{justify-content:flex-end;margin-top:18px}.import-hint{color:#988b8d;font-size:12px;line-height:1.7}.import-hint p{margin:0}.import-upload{width:100%}.import-upload :deep(.el-upload-dragger){width:100%}.picked-file{margin-top:8px;font-size:13px;color:#606266;display:flex;align-items:center;gap:8px}.import-result{margin-top:4px}.import-result .fail-table{margin-top:10px}@media(max-width:650px){.card-header{align-items:flex-start}.header-actions{flex-direction:column}.filters{flex-wrap:wrap}.filters .el-input{max-width:none;flex:1}.card-header p{max-width:220px}}</style>
