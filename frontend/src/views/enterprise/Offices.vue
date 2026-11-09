<template>
  <div class="page" v-loading="loading"><div class="heading"><div><h1>办公场所</h1><p>东风校区为主场所，学生第二生活园区为分场所；可为各场所生成限时签到二维码。</p></div><el-button v-if="user.isAdmin" type="primary" @click="openCreate">新增场所</el-button></div>
    <el-card><el-table :data="rows" stripe empty-text="暂无办公场所"><el-table-column prop="code" label="编码" width="120" /><el-table-column label="场所类型" width="105"><template #default="scope"><el-tag :type="scope.row.code==='HQ'?'success':'info'">{{scope.row.code==='HQ'?'主场所':scope.row.code==='BRANCH'?'分场所':'普通场所'}}</el-tag></template></el-table-column><el-table-column prop="name" label="场所名称" min-width="200" /><el-table-column prop="companyName" label="所属单位" min-width="150" /><el-table-column prop="address" label="地址" min-width="230" /><el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status ? 'success' : 'info'">{{scope.row.status?'启用':'停用'}}</el-tag></template></el-table-column><el-table-column label="操作" width="235" fixed="right"><template #default="scope"><el-button link type="primary" :disabled="!scope.row.status" @click="showQr(scope.row)">签到码</el-button><el-button v-if="scope.row.name || scope.row.address" link @click="openMap(scope.row)">地图</el-button><el-button v-if="user.isAdmin" link @click="openEdit(scope.row)">编辑</el-button></template></el-table-column></el-table></el-card>
    <el-dialog v-model="formOpen" :title="editing ? '编辑办公场所' : '新增办公场所'" width="min(560px,96vw)"><el-form label-position="top" :model="form"><div class="form-grid"><el-form-item label="场所编码"><el-input v-model="form.code" placeholder="如 CAMPUS_EAST" :disabled="!!editing" /></el-form-item><el-form-item label="所属单位"><el-input v-model="form.companyName" placeholder="如郑州轻工业大学" /></el-form-item></div><el-form-item label="场所名称"><el-input v-model="form.name" placeholder="如东风校区" /></el-form-item><el-form-item label="详细地址"><el-input v-model="form.address" placeholder="省、市、街道、门牌号" /></el-form-item><div class="form-grid"><el-form-item label="纬度"><el-input v-model="form.latitude" placeholder="精确定位后填写" /></el-form-item><el-form-item label="经度"><el-input v-model="form.longitude" placeholder="精确定位后填写" /></el-form-item></div><el-button link type="primary" @click="useLocation">使用当前位置获取坐标</el-button><el-form-item v-if="editing" label="状态" style="margin-top:16px"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item></el-form><template #footer><el-button @click="formOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
    <el-dialog v-model="qrOpen" title="办公场所签到二维码" width="min(470px,96vw)" @closed="clearQr"><div class="qr-panel"><b>{{selected?.name}}</b><p>{{selected?.address}}</p><img v-if="qrImage" :src="qrImage" alt="限时签到二维码" /><el-skeleton v-else :rows="3" animated /><span>请用手机自带相机直接扫码，打开链接后登录并确认签到</span><small>二维码每分钟自动更新 · 链接指向 {{qrOrigin}}</small><el-alert v-if="isLocalHost && !configuredQrOrigin" type="warning" :closable="false" title="当前二维码指向 localhost，手机无法打开。请在电脑上改用局域网 IP 地址打开本页。" /><el-button type="primary" plain @click="refreshQr">刷新二维码</el-button><el-button link @click="copyLink">复制签到链接</el-button></div></el-dialog>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { getOffices, createOffice, updateOffice, getQr } from '@/api/enterprise'

const router = useRouter(), user = useUserStore(), rows = ref([]), loading = ref(false), saving = ref(false)
const isLocalHost = ['localhost', '127.0.0.1'].includes(window.location.hostname)
const configuredQrOrigin = (import.meta.env.VITE_QR_PUBLIC_ORIGIN || '').replace(/\/$/, '')
const qrOrigin = isLocalHost && configuredQrOrigin ? configuredQrOrigin : window.location.origin
const formOpen = ref(false), qrOpen = ref(false), editing = ref(null), selected = ref(null), qrImage = ref(''), qrLink = ref('')
const form = reactive({ code:'', companyName:'', name:'', address:'', latitude:'', longitude:'', status:1 })
let timer
async function load() { loading.value=true; try { rows.value=await getOffices() } finally { loading.value=false } }
function resetForm(value={}) { Object.assign(form,{code:value.code||'',companyName:value.companyName||'',name:value.name||'',address:value.address||'',latitude:value.latitude ?? '',longitude:value.longitude ?? '',status:value.status ?? 1}) }
function openCreate() { editing.value=null; resetForm(); formOpen.value=true }
function openEdit(row) { editing.value=row; resetForm(row); formOpen.value=true }
async function save() {
  if (!form.code || !form.companyName || !form.name || !form.address) return ElMessage.warning('请填写编码、单位、名称和地址')
  saving.value=true
  try { const data={...form,latitude:form.latitude===''?null:Number(form.latitude),longitude:form.longitude===''?null:Number(form.longitude)}; if(editing.value) await updateOffice(editing.value.id,data); else await createOffice(data); ElMessage.success('场所信息已保存'); formOpen.value=false; await load() }
  finally { saving.value=false }
}
function useLocation() { if (!navigator.geolocation) return ElMessage.warning('当前浏览器不支持定位'); navigator.geolocation.getCurrentPosition(pos=>{form.latitude=pos.coords.latitude.toFixed(7);form.longitude=pos.coords.longitude.toFixed(7);ElMessage.success('已获取当前坐标')},()=>ElMessage.warning('无法获取位置，请检查浏览器定位权限')) }
function openMap(row) {
  if (Number.isFinite(Number(row.longitude)) && Number.isFinite(Number(row.latitude)) && row.longitude != null && row.latitude != null) {
    const params = new URLSearchParams({ position: `${row.longitude},${row.latitude}`, name: row.name, coordinate: 'gaode', callnative: '0', src: 'lianzhi-attendance' })
    window.open(`https://uri.amap.com/marker?${params}`, '_blank', 'noopener')
    return
  }
  const keyword = encodeURIComponent([row.name, row.address].filter(Boolean).join(' '))
  window.open(`https://uri.amap.com/search?keyword=${keyword}&view=map&src=lianzhi-attendance`, '_blank', 'noopener')
}
async function showQr(row) { selected.value=row; qrOpen.value=true; await refreshQr(); timer=setInterval(refreshQr,60000) }
async function refreshQr() { if(!selected.value || !qrOpen.value)return; const qr=await getQr(selected.value.id); qrLink.value=qrOrigin + router.resolve({path:'/qr-checkin',query:{token:qr.token}}).href; qrImage.value=await QRCode.toDataURL(qrLink.value,{width:270,margin:2,color:{dark:'#153766',light:'#ffffff'}}) }
async function copyLink() { if (!qrLink.value) return; await navigator.clipboard.writeText(qrLink.value); ElMessage.success('签到链接已复制') }
function clearQr() { clearInterval(timer); timer=null; selected.value=null; qrImage.value=''; qrLink.value='' }
onMounted(load); onUnmounted(clearQr)
</script>

<style scoped>
.page{display:grid;gap:20px}.heading{display:flex;justify-content:space-between;align-items:center;gap:15px}.heading h1{font-size:24px;margin-bottom:8px}.heading p{font-size:13px;color:#8b9aaf}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:15px}.qr-panel{text-align:center;display:grid;justify-items:center;gap:8px}.qr-panel b{font-size:19px}.qr-panel p{font-size:12px;color:#8897aa}.qr-panel img{width:270px;height:270px;margin:10px 0;border:1px solid #e2eaf5;border-radius:10px;padding:5px}.qr-panel span{font-size:13px}.qr-panel small{max-width:320px;color:#9aa6b7;line-height:1.5}.qr-panel .el-button{margin:5px 0 0}
@media(max-width:600px){.heading h1{font-size:21px}.form-grid{grid-template-columns:1fr}.page :deep(.el-table){font-size:12px}}
</style>
