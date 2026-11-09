<template>
  <div class="mobile-page">
    <div class="icon"><el-icon><Grid /></el-icon></div>
    <h1>二维码签到</h1>
    <p>用手机自带相机对准管理员展示的动态二维码，点击识别出的链接，再确认签到。</p>
    <el-card class="confirm">
      <div class="row"><span>当前员工</span><b>{{ me.realName || '加载中' }}</b></div>
      <div class="row"><span>所属场所</span><b>{{ me.officeName || '未分配' }}</b></div>
      <div class="row"><span>签到码</span><b :class="token ? 'ready' : 'missing'">{{ token ? '已读取' : '等待扫描' }}</b></div>
      <div class="scanner">
        <el-button v-if="canLiveScan" type="primary" @click="scanning ? stopScan() : startScan()">{{ scanning ? '关闭扫一扫' : '扫一扫' }}</el-button>
        <div class="manual"><el-input v-model="manualLink" placeholder="也可粘贴管理员复制的签到链接" clearable @keyup.enter="applyManualLink" /><el-button @click="applyManualLink">读取</el-button></div>
      </div>
      <el-alert v-if="!canLiveScan && !token" type="info" :closable="false" title="当前局域网 HTTP 页面不支持内置扫一扫。请退出网页，用手机自带相机扫描管理员屏幕上的二维码。" />
      <div v-show="scanning" class="scan-preview"><video ref="scanVideo" autoplay muted playsinline></video><span>将二维码放入画面，识别后会自动停止</span></div>
      <el-alert v-if="meLoaded && !me.officeId" type="warning" :closable="false" title="该员工未分配办公场所，请联系管理员分配后重新扫码。" />
      <el-alert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" style="margin-top:14px" />
      <el-button class="confirm-button" type="primary" size="large" :disabled="!token || !me.officeId || !!result" :loading="submitting || loadingMe" @click="submit">{{ result ? '已签到' : '确认签到' }}</el-button>
    </el-card>
    <div v-if="result" class="success"><el-icon><CircleCheck /></el-icon><h2>签到成功</h2><p>{{ result.officeName }} · {{ format(result.checkInTime) }}</p><router-link to="/records">查看考勤记录 →</router-link></div>
    <div class="hint">签到码约两分钟内有效，且仅适用于员工所属场所；每人每天只能签到一次。过期时请扫描管理员页面更新后的码。</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Grid, CircleCheck } from '@element-plus/icons-vue'
import jsQR from 'jsqr'
import { getMe, qrCheckIn } from '@/api/enterprise'

const route = useRoute()
const me = ref({}), submitting = ref(false), loadingMe = ref(true), meLoaded = ref(false), result = ref(null), errorMessage = ref('')
const manualLink = ref(''), scannedToken = ref('')
const scanVideo = ref(null), scanning = ref(false)
let stream, scanFrame = 0, lastScanAt = 0
const token = computed(() => scannedToken.value || (typeof route.query.token === 'string' ? route.query.token : ''))
const canLiveScan = window.isSecureContext && !!navigator.mediaDevices?.getUserMedia
const format = value => String(value || '').replace('T', ' ').slice(0, 16)

function readToken(text) {
  const value = String(text || '').trim()
  let found = value
  if (/^https?:\/\//i.test(value)) {
    const url = new URL(value)
    if (!url.pathname.endsWith('/qr-checkin')) throw new Error('这不是本系统的签到二维码，请扫描管理员展示的签到码。')
    found = url.searchParams.get('token') || ''
  }
  if (!/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(found) || found.length > 300) {
    throw new Error('未读取到有效签到码，请重新扫描或粘贴签到链接。')
  }
  scannedToken.value = found
  stopScan()
  errorMessage.value = ''
  result.value = null
  ElMessage.info('签到码已读取，请点击“确认签到”')
}

function stopScan() {
  scanning.value = false
  cancelAnimationFrame(scanFrame)
  scanFrame = 0
  stream?.getTracks().forEach(track => track.stop())
  stream = null
  if (scanVideo.value) scanVideo.value.srcObject = null
}

function scanLoop(timestamp) {
  if (!scanning.value || !scanVideo.value) return
  const video = scanVideo.value
  if (timestamp - lastScanAt >= 100 && video.readyState >= 2 && video.videoWidth > 0) {
    lastScanAt = timestamp
    const canvas = document.createElement('canvas')
    const scale = Math.min(1, 720 / video.videoWidth)
    canvas.width = Math.max(1, Math.round(video.videoWidth * scale))
    canvas.height = Math.max(1, Math.round(video.videoHeight * scale))
    const context = canvas.getContext('2d', { willReadFrequently: true })
    context.drawImage(video, 0, 0, canvas.width, canvas.height)
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height)
    const code = jsQR(pixels.data, canvas.width, canvas.height, { inversionAttempts: 'dontInvert' })
    if (code) {
      try { readToken(code.data); return }
      catch (error) { errorMessage.value = error?.message || '二维码无效，请扫描管理员页面的新二维码。' }
    }
  }
  scanFrame = requestAnimationFrame(scanLoop)
}

async function startScan() {
  errorMessage.value = ''
  if (!navigator.mediaDevices?.getUserMedia) {
    errorMessage.value = '当前网址无法打开实时摄像头。请用手机自带相机直接扫描管理员二维码，或改用 HTTPS 地址。'
    return
  }
  scanning.value = true
  try {
    await nextTick()
    stream = await navigator.mediaDevices.getUserMedia({ audio: false, video: { facingMode: { ideal: 'environment' } } })
    if (!scanning.value) { stream.getTracks().forEach(track => track.stop()); stream = null; return }
    scanVideo.value.srcObject = stream
    await scanVideo.value.play()
    scanFrame = requestAnimationFrame(scanLoop)
  } catch (error) {
    stopScan()
    errorMessage.value = error?.name === 'NotAllowedError' ? '摄像头权限被拒绝，请在浏览器中允许访问摄像头。' : '无法打开摄像头，请使用手机自带相机扫描二维码。'
  }
}

function applyManualLink() {
  try { readToken(manualLink.value) }
  catch (error) { errorMessage.value = error.message }
}

async function submit() {
  errorMessage.value = ''
  if (!token.value) { errorMessage.value = '请先扫描二维码或粘贴签到链接。'; return }
  if (!me.value.officeId) { errorMessage.value = '当前员工未分配办公场所，请联系管理员。'; return }
  submitting.value = true
  try { result.value = await qrCheckIn(token.value); ElMessage.success('签到已记录') }
  catch (error) { errorMessage.value = error?.message || '签到失败，请重新扫码后重试。' }
  finally { submitting.value = false }
}

onMounted(async () => {
  try { me.value = await getMe(); meLoaded.value = true }
  catch (error) { errorMessage.value = error?.message || '员工信息加载失败，请重新登录。' }
  finally { loadingMe.value = false }
})
onBeforeUnmount(stopScan)
</script>

<style scoped>
.mobile-page{max-width:540px;margin:20px auto 0;text-align:center}.icon{width:66px;height:66px;margin:0 auto 18px;border-radius:19px;background:#e9f2ff;color:#286de1;display:grid;place-items:center;font-size:32px}.mobile-page h1{font-size:25px}.mobile-page>p{font-size:13px;color:#8695aa;line-height:1.7;margin:12px auto 24px;max-width:410px}.confirm{text-align:left}.row{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #edf1f7;padding:16px 0;font-size:14px}.row:first-child{padding-top:0}.row span{color:#8494aa}.row b{font-weight:600}.ready{color:#15a377}.missing{color:#c38a2c}.scanner{display:flex;gap:8px;flex-wrap:wrap;margin-top:20px}.scanner>.el-button{margin-left:0}.manual{width:100%;display:flex;gap:8px;margin-top:8px}.manual .el-input{flex:1}.confirm .el-alert{margin-top:18px}.confirm-button{width:100%;margin-top:22px}.success{margin-top:22px;background:#eaf9f0;border:1px solid #c9efd8;border-radius:14px;padding:25px;color:#138b5e}.success .el-icon{font-size:42px}.success h2{font-size:20px;margin:7px}.success p{font-size:13px;margin:0 0 12px}.success a{font-size:13px;color:#0a7b52}.hint{margin:26px auto;color:#9aa6b6;font-size:12px;line-height:1.6}
.scan-preview{position:relative;margin-top:16px;border-radius:12px;overflow:hidden;background:#14243d}.scan-preview video{display:block;width:100%;max-height:370px;object-fit:cover}.scan-preview span{position:absolute;left:0;right:0;bottom:0;padding:10px;background:#14243dbb;color:#fff;font-size:13px;text-align:center}
</style>
