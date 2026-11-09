<template>
  <div class="face-page"><div class="heading"><div><h1>人脸考勤</h1><p>使用当前设备摄像头录入本人面部特征，并通过真实识别完成签到。</p></div><el-tag :type="me.faceRegistered?'success':'warning'">{{me.faceRegistered?'已录入人脸':'待录入人脸'}}</el-tag></div>
    <div class="face-grid"><el-card class="camera-card"><div class="camera" :class="{live:ready}"><video ref="video" autoplay muted playsinline></video><div v-if="!ready" class="camera-cover"><el-icon><Camera /></el-icon><b>摄像头尚未开启</b><span>点击下方按钮授权摄像头</span></div><div v-else class="guide"></div><div v-if="challengeText" class="challenge-cue">{{ challengeText }}</div></div><div v-if="me.faceRegistered" class="enhanced-option"><el-switch v-model="enhanced" :disabled="processing" /><span>增强活体检测（眨眼或张嘴一次）</span></div><div class="camera-actions"><el-button v-if="!ready" type="primary" size="large" @click="startCamera">开启摄像头</el-button><template v-else><el-button v-if="!me.faceRegistered" type="primary" size="large" :loading="processing" @click="enroll">拍摄并录入人脸</el-button><el-button v-else type="primary" size="large" :loading="processing" :disabled="!me.officeId" @click="verify">{{ enhanced?'活体识别并签到':'拍照识别并签到' }}</el-button><el-button size="large" :disabled="processing" @click="stopCamera">关闭</el-button></template></div><p class="camera-tip">默认只需正对镜头拍照，系统会将照片与本人已录入的人脸特征比对。增强活体只需眨眼或张嘴一次。原始照片处理后删除。</p></el-card>
      <el-card class="status-card"><h2>签到信息</h2><div class="detail"><span>员工姓名</span><b>{{me.realName||'—'}}</b></div><div class="detail"><span>员工工号</span><b>{{me.employeeNumber||'—'}}</b></div><div class="detail"><span>办公场所</span><b>{{me.officeName||'未分配'}}</b></div><div class="detail"><span>人脸状态</span><b>{{me.faceRegistered?'已录入':'请先录入'}}</b></div><div class="detail"><span>今日签到</span><b :class="today?'green':'amber'">{{today ? `${todayCheckInCount} 次` : '待签到'}}</b></div><el-alert v-if="!me.officeId" type="warning" :closable="false" title="请联系管理员分配办公场所后再签到" /><el-alert v-else-if="!me.faceRegistered" type="info" :closable="false" title="第一次使用请开启摄像头录入人脸" /><el-alert v-else-if="today" type="success" :closable="false" :title="`最近一次于 ${format(today.checkInTime)} 签到，可继续人脸签到；每次均保留记录`" /></el-card>
    </div><canvas ref="canvas" class="hidden-canvas"></canvas>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { getMe, getDashboard, faceRegister, faceCheckIn } from '@/api/enterprise'

const video=ref(null), canvas=ref(null), me=ref({}), today=ref(null), todayCheckInCount=ref(0), ready=ref(false), processing=ref(false), challengeText=ref(''), enhanced=ref(false)
let stream
const format=value=>String(value||'').replace('T',' ').slice(11,16)
async function load() { const [profile,stats]=await Promise.all([getMe(),getDashboard()]); me.value=profile; today.value=stats.myToday; todayCheckInCount.value=stats.todayCheckInCount ?? (stats.myToday ? 1 : 0) }
async function startCamera() {
  if (!navigator.mediaDevices?.getUserMedia) return ElMessage.error('当前浏览器无法使用摄像头。请在电脑 localhost 或 HTTPS 页面打开。')
  try { stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:'user',width:{ideal:640},height:{ideal:480}},audio:false}); video.value.srcObject=stream; await video.value.play(); ready.value=true }
  catch (error) { ElMessage.error(`摄像头开启失败：${error.message||'请检查权限'}`) }
}
function stopCamera() { if (stream) stream.getTracks().forEach(track=>track.stop()); stream=null; if(video.value) video.value.srcObject=null; ready.value=false }
function capture(quality=.82) { return new Promise((resolve,reject)=>{ const v=video.value; if(!v?.videoWidth) return reject(new Error('摄像头画面尚未就绪')); const c=canvas.value; c.width=v.videoWidth; c.height=v.videoHeight; c.getContext('2d').drawImage(v,0,0,c.width,c.height); c.toBlob(blob=>blob?resolve(blob):reject(new Error('无法拍摄画面')),'image/jpeg',quality) }) }
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
async function captureChallenge() {
  challengeText.value='请正对镜头，准备做一次动作'; await pause(1000)
  const steps=[
    { text:'保持正脸', frames:5 },
    { text:'现在眨眼或张嘴一次，然后恢复自然表情', frames:20 },
    { text:'保持正脸，等待验证', frames:5 }
  ]
  const frames=[]
  for (const step of steps) {
    challengeText.value=step.text
    for (let i=0; i<step.frames; i++) { frames.push(await capture(.68)); await pause(180) }
  }
  return frames
}
async function enroll() { processing.value=true; try { await faceRegister(await capture()); ElMessage.success('人脸录入成功'); await load() } catch(error) { if(!error.messageShown) ElMessage.error(error.message) } finally { processing.value=false } }
async function verify() { processing.value=true; try { const frames=enhanced.value?await captureChallenge():[]; const record=await faceCheckIn(await capture(),frames,enhanced.value); today.value=record; todayCheckInCount.value+=1; ElMessage.success(`第 ${todayCheckInCount.value} 次人脸签到成功，时间 ${format(record.checkInTime)}`) } catch(error) { if(!error.messageShown) ElMessage.error(error.message) } finally { challengeText.value=''; processing.value=false } }
onMounted(load); onUnmounted(stopCamera)
</script>

<style scoped>
.face-page{display:grid;gap:20px}.heading{display:flex;justify-content:space-between;align-items:center;gap:15px}.heading h1{font-size:24px;margin-bottom:8px}.heading p{font-size:13px;color:#8695a9}.face-grid{display:grid;grid-template-columns:1.45fr 1fr;gap:20px}.camera{height:390px;background:#0b1729;border-radius:15px;position:relative;overflow:hidden;display:grid;place-items:center}.camera video{width:100%;height:100%;object-fit:cover;transform:scaleX(-1)}.camera-cover{position:absolute;inset:0;display:grid;place-content:center;justify-items:center;gap:10px;color:#c6d3e5}.camera-cover .el-icon{font-size:48px;color:#6ca8f0}.camera-cover span{font-size:12px;color:#879bb4}.guide{position:absolute;inset:11% 28%;border:2px dashed rgba(255,255,255,.58);border-radius:48%;box-shadow:0 0 0 200px rgba(6,20,39,.25)}.camera-actions{display:flex;justify-content:center;gap:9px;margin:18px 0}.camera-tip{text-align:center;font-size:12px;color:#96a3b5;line-height:1.6}.status-card h2{font-size:19px;margin-bottom:22px}.detail{display:flex;justify-content:space-between;border-bottom:1px solid #edf1f6;padding:16px 0;font-size:13px}.detail span{color:#8795a8}.detail b{font-weight:600}.detail .green{color:#139a70}.detail .amber{color:#bc8128}.status-card .el-alert{margin-top:23px}.hidden-canvas{display:none}@media(max-width:950px){.face-grid{grid-template-columns:1fr}.camera{height:330px}}@media(max-width:600px){.camera{height:270px}.guide{inset:12% 25%}.heading h1{font-size:21px}.camera-actions{flex-wrap:wrap}}
.challenge-cue{position:absolute;left:8%;right:8%;bottom:22px;background:rgba(6,26,57,.88);border:1px solid rgba(255,255,255,.25);border-radius:12px;padding:12px 16px;color:#fff;text-align:center;font-size:16px;font-weight:700;z-index:2}.enhanced-option{display:flex;justify-content:center;align-items:center;gap:8px;margin-top:16px;color:#718198;font-size:12px}
</style>
