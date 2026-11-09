<template>
  <div class="face-recognition">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>人脸签到</span>
        </div>
      </template>
      <div class="sign-layout">
      <aside class="sign-controls">
      <span class="eyebrow">FACE CHECK-IN</span><h2>快速完成签到</h2>
      <p class="intro">{{ groupMode ? '先勾选本次成员，再由每位成员分别完成眨眼、转头和身份核验。' : requireLiveness ? '依次完成眨眼、左右转头和身份核验。' : '打开摄像头后直接核验本人身份，快速完成签到。' }}</p>
      <el-form :model="form" label-position="top">
        <el-form-item label="选择课程">
          <el-select :disabled="checkingIn" v-model="form.courseId" placeholder="请选择课程" style="width: 100%;">
            <el-option 
              v-for="course in myCourses" 
              :key="course.id" 
              :label="formatCourseOption(course)" 
              :value="course.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="detection-mode"><b>检测方式</b><el-radio-group v-model="detectionMode" :disabled="checkingIn || groupMode"><el-radio-button label="quick">快速检测</el-radio-button><el-radio-button label="live">活体检测</el-radio-button></el-radio-group><small>{{requireLiveness?'需依次完成眨眼和左右转头，安全性更高':'打开摄像头后直接识别人脸，适合课堂快速签到'}}</small></div>

      <div v-if="leaderGroups.length" class="quick-options">
        <el-checkbox :disabled="checkingIn" :model-value="groupMode" @change="toggleGroupMode">小组快速检测</el-checkbox>
        <small v-if="!form.courseId">请先选择你担任小组长的课程，再按人员勾选检测</small>
        <small v-else-if="!currentLeaderGroup">当前不是你管理的课程；点击上方选项可自动切换</small>
        <el-checkbox-group v-if="groupMode && currentLeaderGroup" v-model="selectedMembers" :disabled="checkingIn" class="member-list"><el-checkbox v-for="member in currentLeaderGroup.members" :key="member.id" :label="member.id">{{member.realName}} · {{member.studentId}} {{completed.includes(member.id)?'（已签到）':''}}</el-checkbox></el-checkbox-group>
        <small v-if="groupMode">已选择 {{selectedMembers.length}} 人；每人必须完成活体检测</small>
      </div>

      <AttendanceSession v-if="form.courseId" :course-id="form.courseId" compact />
      <div class="step-note"><span>1</span>确认课程 <i></i><span>2</span>{{ requireLiveness ? '完成活体动作' : '正对镜头' }} <i></i><span>3</span>核验签到</div>
      </aside>

      <div class="recognition-content">
        <div class="stage-label"><span :class="{online:cameraActive}"></span>{{ stageTitle }}</div>
        <div class="stage-steps">
          <div v-for="(item,index) in stages" :key="item.key" :class="{active:stageIndex===index,done:stageIndex>index}"><i>{{stageIndex>index?'✓':index+1}}</i><span>{{item.label}}</span></div>
        </div>
        <div class="camera-area">
          <video 
            v-if="cameraActive" 
            ref="videoElement" 
            autoplay 
            playsinline
            class="video-stream"
          ></video>
          <div v-if="cameraActive && phase!=='success'" class="face-guide" :class="[`phase-${phase}`,{scanning:checkingIn}]">
            <i class="corner tl"></i><i class="corner tr"></i><i class="corner bl"></i><i class="corner br"></i><span v-if="checkingIn" class="scan-line"></span>
          </div>
          <div v-if="livenessPrompt" class="liveness-prompt">{{ livenessPrompt }}</div>
          <div v-if="checkingIn && phase!=='success'" class="live-progress"><div><span>{{ progressText }}</span><b>{{ livenessProgress }}%</b></div><el-progress :percentage="livenessProgress" :show-text="false" :stroke-width="6"/></div>
          <transition name="success-pop"><div v-if="phase==='success' && recognitionResult" class="success-overlay"><div class="success-check"><el-icon><Check /></el-icon></div><h2>{{recognitionResult.status==='LATE'?'签到成功（迟到）':'签到成功'}}</h2><p>{{recognitionResult.realName}} · {{recognitionResult.studentId||'未设置学号'}}</p><span>{{ redirectSeconds }} 秒后自动查看考勤记录</span></div></transition>
          <div v-if="!cameraActive" class="placeholder-camera">
            <div class="camera-icon"><el-icon :size="42"><Camera /></el-icon></div><h3>人脸核验区域</h3><p>开启摄像头后将显示实时画面</p>
          </div>
        </div>

        <div v-if="friendlyError" class="error-guide"><el-icon><WarningFilled /></el-icon><div><b>{{friendlyError.title}}</b><span>{{friendlyError.tip}}</span></div><el-button link type="primary" @click="retry">重新检测</el-button></div>

        <el-card v-if="recognitionResult" class="recognition-result" shadow="never">
          <template #header>
            <div class="result-header">{{recognitionResult.status === 'LATE' ? '签到成功（迟到）' : '签到成功'}}</div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="学号">
              {{ recognitionResult.studentId || '未设置' }}
            </el-descriptions-item>
            <el-descriptions-item label="姓名">
              {{ recognitionResult.realName || '未知' }}
            </el-descriptions-item>
            <el-descriptions-item label="签到时间">
              {{ formatRecognitionTime(recognitionResult.recognitionTime) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </div>
      <div class="sign-actions">
        <div class="button-group">
          <el-button type="primary" size="large" @click="toggleCamera" :loading="cameraLoading" :disabled="checkingIn">{{ cameraActive ? '关闭摄像头' : '开启摄像头' }}</el-button>
          <el-button type="success" size="large" :disabled="!cameraActive || !form.courseId || !videoReady || (groupMode && !selectedMembers.length)" @click="captureAndCheckIn" :loading="checkingIn">{{ groupMode ? '检测下一位成员' : requireLiveness ? '开始活体检测' : '立即人脸签到' }}</el-button>
        </div>
        <div class="safe-note">为防止照片代签，签到仅支持实时摄像头采集</div>
      </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AttendanceSession from '@/components/AttendanceSession.vue'
import { Camera, Check, WarningFilled } from '@element-plus/icons-vue'
import { getEnrolledCourses } from '@/api/course'
import { checkIn } from '@/api/attendance'
import { verifyFace, validateLivenessStep } from '@/api/face'
import { getMyGroup } from '@/api/group'
import dayjs from 'dayjs'

const videoElement = ref(null)
const router = useRouter()
const route = useRoute()
const cameraActive = ref(false)
const cameraLoading = ref(false)
const checkingIn = ref(false)
const myCourses = ref([])
const streamRef = ref(null)
const videoReady = ref(false)  // 视频是否加载完成
const recognitionResult = ref(null)
const livenessPrompt = ref('')
const form = ref({
  courseId: null
})
const myGroups = ref([])
const groupMode = ref(false)
const detectionMode = ref('quick')
const requireLiveness = computed(() => groupMode.value || detectionMode.value === 'live')
const leaderGroups = computed(() => myGroups.value.filter(group => group.isLeader))
const currentLeaderGroup = computed(() => leaderGroups.value.find(group => Number(group.courseId) === Number(form.value.courseId)) || null)
const selectedMembers=ref([]),completed=ref([])
const phase=ref('idle'),livenessProgress=ref(0),friendlyError=ref(null),redirectSeconds=ref(3)
let redirectTimer
const stages=computed(()=>requireLiveness.value?[{key:'ready',label:'正视镜头'},{key:'blink',label:'完成眨眼'},{key:'turnLeft',label:'向左转头'},{key:'turnRight',label:'向右转头'},{key:'verify',label:'身份核验'},{key:'success',label:'签到完成'}]:[{key:'ready',label:'正视镜头'},{key:'verify',label:'身份核验'},{key:'success',label:'签到完成'}])
const stageIndex=computed(()=>requireLiveness.value?({idle:0,align:0,blink:1,turnLeft:2,turnRight:3,verify:4,success:5,error:0}[phase.value]??0):({idle:0,align:0,verify:1,success:2,error:0}[phase.value]??0))
const stageTitle=computed(()=>phase.value==='success'?'签到已完成':cameraActive.value?'摄像头已连接 · 请将脸移入框内':'等待开启摄像头')
const progressText=computed(()=>phase.value==='align'?'正在校验清晰正脸':phase.value==='blink'?'正在校验眨眼动作':phase.value==='turnLeft'?'正在校验向左转头':phase.value==='turnRight'?'正在校验向右转头':phase.value==='verify'?'正在核验身份':'准备检测')

watch(groupMode,value=>{selectedMembers.value=[];completed.value=[];if(value)detectionMode.value='live'})
watch(()=>form.value.courseId,()=>{if(groupMode.value&&!currentLeaderGroup.value)groupMode.value=false;selectedMembers.value=[];recognitionResult.value=null;completed.value=[];resetFeedback()})

const toggleGroupMode = value => {
  if (!value) {
    groupMode.value = false
    return
  }
  if (currentLeaderGroup.value) {
    groupMode.value = true
    return
  }
  if (leaderGroups.value.length === 1) {
    const targetGroup = leaderGroups.value[0]
    form.value.courseId = Number(targetGroup.courseId)
    groupMode.value = true
    ElMessage.success(`已自动切换到「${targetGroup.courseName || '小组课程'}」`)
    return
  }
  ElMessage.warning('请先选择你担任小组长的课程')
}

const weekNames = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
const shortTime = (value) => String(value || '').slice(0, 5)
const formatCourseOption = (course) => {
  const day = weekNames[course.weekDay] || ''
  const time = course.startTime && course.endTime ? `${shortTime(course.startTime)}–${shortTime(course.endTime)}` : ''
  return [course.courseName, day, time].filter(Boolean).join(' · ')
}

const formatRecognitionTime = (time) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

const showRecognitionResult = (result, attendance) => {
  recognitionResult.value = {
    studentId: result?.studentId,
    realName: result?.realName,
    status: attendance?.status,
    recognitionTime: attendance?.checkInTime || new Date().toISOString()
  }
  phase.value='success';livenessProgress.value=100
}

const resetFeedback=()=>{phase.value=cameraActive.value?'align':'idle';livenessProgress.value=0;friendlyError.value=null;livenessPrompt.value=''}
const retry=()=>{resetFeedback();recognitionResult.value=null}
const explainError=error=>{const message=error?.message||'';if(message.includes('左转'))return{title:'左转这一步未通过',tip:message};if(message.includes('右转'))return{title:'右转这一步未通过',tip:message};if(message.includes('转头'))return{title:'没有检测到完整转头',tip:message||'请缓慢向左、向右转头，全程不要离开取景框'};if(message.includes('眨眼'))return{title:'眨眼这一步未通过',tip:message};if(message.includes('活体'))return{title:'活体检测未完成',tip:message||'请按页面提示依次完成各步骤'};if(message.includes('人脸不一致')||message.includes('不匹配')||message.includes('非注册'))return{title:'人脸与账号不匹配',tip:'请确认由账号本人正对镜头完成签到'};if(message.includes('单人')||message.includes('多')&&message.includes('脸'))return{title:'画面中人数不符合要求',tip:message};if(message.includes('清晰')||message.includes('质量')||message.includes('正脸'))return{title:'正脸这一步未通过',tip:message||'请正视镜头，保持光线充足，并移除遮挡物'};if(message.includes('签到')&&message.includes('结束'))return{title:'本次签到已经结束',tip:'请联系任课教师确认是否可以补签'};if(message.includes('服务')||message.includes('Network')||message.includes('timeout'))return{title:'识别服务暂时无法连接',tip:'请检查服务后重试'};return{title:'当前步骤未通过',tip:message||'该步骤没有正确检测到，请重新检测'}}
const startRedirect=()=>{if(groupMode.value)return;redirectSeconds.value=3;clearInterval(redirectTimer);redirectTimer=setInterval(()=>{redirectSeconds.value--;if(redirectSeconds.value<=0){clearInterval(redirectTimer);router.push('/attendance')}},1000)}

// 获取我的课程
const loadMyCourses = async () => {
  try {
    const res = await getEnrolledCourses()
    myCourses.value = res
    const requestedId = Number(route.query.courseId)
    if (requestedId && res.some(course => Number(course.id) === requestedId)) form.value.courseId = requestedId
  } catch (error) {
    console.error('加载课程失败:', error)
  }
}

// 切换摄像头
const toggleCamera = async () => {
  if (cameraActive.value) {
    stopCamera()
  } else {
    await startCamera()
  }
}

// 开启摄像头
const startCamera = async () => {
  cameraLoading.value = true
  videoReady.value = false
  try {
    console.log('正在请求摄像头权限...')
    const stream = await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: 720 },
        height: { ideal: 960 },
        facingMode: { ideal: 'user' }
      },
      audio: false
    })
    console.log('摄像头权限获取成功，stream:', stream)
    streamRef.value = stream
    cameraActive.value = true
    phase.value='align';friendlyError.value=null

    // 等待 video 元素渲染
    await nextTick()
    console.log('videoElement:', videoElement.value)

    if (videoElement.value) {
      videoElement.value.srcObject = stream
      // 等待视频数据加载完成
      videoElement.value.onloadedmetadata = () => {
        console.log('视频元数据加载完成')
        videoElement.value.play()
      }
      videoElement.value.onloadeddata = () => {
        videoReady.value = true
        console.log('视频数据加载完成, 尺寸:', videoElement.value.videoWidth, 'x', videoElement.value.videoHeight)
      }
      videoElement.value.onerror = (e) => {
        console.error('视频播放错误:', e)
      }
    }
    ElMessage.success('摄像头已开启')
  } catch (error) {
    console.error('无法访问摄像头:', error.name, error.message)
    if (error.name === 'NotAllowedError') {
      ElMessage.error('请允许浏览器访问摄像头权限')
    } else if (error.name === 'NotFoundError') {
      ElMessage.error('未检测到摄像头设备')
    } else if (error.name === 'NotReadableError') {
      ElMessage.error('摄像头可能被其他程序占用')
    } else {
      ElMessage.error('无法访问摄像头: ' + error.message)
    }
  } finally {
    cameraLoading.value = false
  }
}

// 关闭摄像头
const stopCamera = () => {
  videoReady.value = false
  if (streamRef.value) {
    streamRef.value.getTracks().forEach(track => track.stop())
    streamRef.value = null
  }
  cameraActive.value = false
  phase.value='idle';livenessProgress.value=0
}

const wait = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds))
const captureFrame = () => new Promise((resolve, reject) => {
  const video = videoElement.value
  const canvas = document.createElement('canvas')
  // 人脸模型不需要上传摄像头原始大图；缩放后可明显减少热点传输与服务处理时间。
  const scale = Math.min(1, 480 / video.videoWidth)
  canvas.width = Math.round(video.videoWidth * scale)
  canvas.height = Math.round(video.videoHeight * scale)
  const context = canvas.getContext('2d')
  context.drawImage(video, 0, 0, canvas.width, canvas.height)
  canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('无法生成摄像头画面')), 'image/jpeg', 0.78)
})

// 动作提示出现后先给用户留出反应时间，再连续取帧，避免眨眼发生在采集窗口之外。
const collectLivenessFrames = async (frames, count, interval, onProgress) => {
  for (let index = 0; index < count; index++) {
    await wait(interval)
    frames.push(await captureFrame())
    onProgress(index)
  }
}

const passLivenessGate = async (step, frames) => {
  const data = new FormData()
  data.append('step', step)
  frames.forEach((frame, index) => data.append('frames', frame, `${step}-${index}.jpg`))
  const result = await validateLivenessStep(data)
  if (!result?.passed) throw new Error('该步骤没有正确检测到')
  return result
}

// 先采集正脸，再依次采集眨眼和左右转头动作；单张照片不能完成签到。
const captureAndCheckIn = async () => {
  if (!videoElement.value || !form.value.courseId) {
    ElMessage.warning('请先开启摄像头并选择课程')
    return
  }
  checkingIn.value = true
  recognitionResult.value = null
  friendlyError.value=null;phase.value='align';livenessProgress.value=5
  try {
    if(groupMode.value){
      if(!currentLeaderGroup.value) throw new Error('当前课程没有可管理的小组');
      if(!selectedMembers.value.length) throw new Error('请先勾选本次签到成员');
      await ElMessageBox.confirm('本次已选：'+currentLeaderGroup.value.members.filter(m=>selectedMembers.value.includes(m.id)).map(m=>m.realName).join('、')+'。每位成员须分别完成眨眼、左右转头和身份核验。','确认签到成员',{confirmButtonText:'开始核验',cancelButtonText:'返回选择'});
    }
    livenessPrompt.value = '第 1 关：请正对镜头，保持脸部完整'
    await wait(500)
    const alignFrames = []
    await collectLivenessFrames(alignFrames,3,180,index=>{livenessProgress.value=7+index*3})
    await passLivenessGate('align', alignFrames)
    const primaryFrame = alignFrames[alignFrames.length - 1]
    livenessProgress.value=18
    const livenessFrames = []
    if (requireLiveness.value) {
      livenessFrames.push(primaryFrame)
      const blinkFrames = []
      livenessPrompt.value = '第 2 关：请自然眨眼 1–2 次，无需保持闭眼'
      phase.value='blink'
      await wait(450)
      await collectLivenessFrames(blinkFrames,32,85,index=>{livenessProgress.value=20+Math.round(index*30/31)})
      await passLivenessGate('blink', blinkFrames)
      livenessFrames.push(...blinkFrames)
      livenessProgress.value=55
      ElMessage.success('第 2 关通过：已检测到完整眨眼')

      const turnBaseline = [...alignFrames]
      const leftFrames = []
      phase.value='turnLeft'
      livenessPrompt.value='第 3 关：请缓慢向左转头'
      await wait(500)
      await collectLivenessFrames(leftFrames,5,180,index=>{livenessProgress.value=58+index*3})
      await passLivenessGate('turn_left', [...turnBaseline,...leftFrames])
      livenessFrames.push(...leftFrames)
      livenessProgress.value=72
      ElMessage.success('第 3 关通过：已检测到向左转头')

      const rightFrames = []
      phase.value='turnRight'
      livenessPrompt.value='第 4 关：请从左侧缓慢转向右侧'
      await wait(500)
      await collectLivenessFrames(rightFrames,6,180,index=>{livenessProgress.value=74+index*2})
      await passLivenessGate('turn_right', [...turnBaseline,...leftFrames,...rightFrames])
      livenessFrames.push(...rightFrames)
      livenessProgress.value=87
      ElMessage.success('第 4 关通过：已检测到向右转头')

      livenessPrompt.value='请回到正面，准备核验身份'
      for(let i=0;i<3;i++){await wait(160);livenessFrames.push(await captureFrame())}
    }
    livenessPrompt.value = '正在核验身份…'
    phase.value='verify';livenessProgress.value=92

    const formData = new FormData()
    formData.append('faceImage', primaryFrame, 'face.jpg')
    formData.append('courseId', form.value.courseId)
    formData.append('groupMode', groupMode.value)
    formData.append('requireLiveness', requireLiveness.value)
    if(groupMode.value)formData.append('selectedMemberIds',selectedMembers.value.join(','))
    livenessFrames.forEach((frame, index) => formData.append('livenessFrames', frame, `live-${index}.jpg`))

    const faceResult = await verifyFace(formData)
    const attendanceResult = await checkIn({ courseId: form.value.courseId, targetStudentId: faceResult.userId })
    showRecognitionResult(faceResult,attendanceResult)
    if(groupMode.value){completed.value.push(faceResult.userId);selectedMembers.value=selectedMembers.value.filter(id=>id!==faceResult.userId)}
    ElMessage.success({
      message: attendanceResult?.status === 'LATE' ? '签到成功（迟到）' : '签到成功',
      duration: 5000
    })
    startRedirect()
  } catch (error) {
    if (error!=='cancel' && error!=='close'){phase.value='error';livenessProgress.value=0;friendlyError.value=explainError(error);if(!error.messageShown)ElMessage.error({message:friendlyError.value.title,duration:5000,showClose:true})}
  } finally {
    if(phase.value!=='success')livenessPrompt.value = ''
    checkingIn.value = false
  }
}

onMounted(() => {
  loadMyCourses()
  getMyGroup().then(result => { myGroups.value = Array.isArray(result) ? result : [] }).catch(() => {})
})

onUnmounted(() => {
  clearInterval(redirectTimer)
  stopCamera()
})
</script>

<style scoped>
.face-recognition {
  padding: 0;
}

.card-header {
  font-weight: bold;
  font-size: 16px;
}

.recognition-content {
  text-align: center;
}
.sign-layout{display:grid;grid-template-columns:minmax(320px,.8fr) minmax(430px,1.2fr);grid-template-areas:"controls camera" "actions camera";column-gap:54px;row-gap:18px;align-items:start;max-width:1040px;margin:0 auto;padding:44px 30px}.sign-controls{grid-area:controls;text-align:left}.recognition-content{grid-area:camera}.sign-actions{grid-area:actions}.eyebrow{font-size:12px;font-weight:700;letter-spacing:1.5px;color:var(--brand)}.sign-controls h2{font-size:26px;margin:10px 0;color:var(--text)}.intro{color:#706668;line-height:1.7;margin-bottom:26px}.step-note{display:flex;align-items:center;gap:8px;color:#706668;font-size:12px;margin:8px 0 0}.step-note span{width:24px;height:24px;flex:0 0 24px;display:grid;place-items:center;background:var(--brand-soft);color:var(--brand);border-radius:50%;font-weight:700}.step-note i{width:16px;height:1px;background:#d0d5dd}.safe-note{font-size:12px;color:#98a2b3;margin-top:14px}.stage-label{display:flex;align-items:center;gap:8px;color:#706668;font-size:13px;margin-bottom:12px;text-align:left}.stage-label span{width:8px;height:8px;border-radius:50%;background:#98a2b3}.camera-icon{width:76px;height:76px;display:grid;place-items:center;border-radius:20px;background:rgba(255,255,255,.08);color:#d0d5dd;margin:0 auto 18px}.placeholder-camera h3{color:#f2f4f7;font-size:18px;margin:0 0 8px}.placeholder-camera p{color:#98a2b3;font-size:13px}
.detection-mode{display:grid;gap:9px;padding:14px;margin:4px 0 16px;background:#faf7f5;border:1px solid var(--border);border-radius:11px}.detection-mode>b{font-size:13px}.detection-mode small{color:#8b7e80;font-size:12px;line-height:1.5}.detection-mode .el-radio-group{width:100%}.detection-mode :deep(.el-radio-button){flex:1}.detection-mode :deep(.el-radio-button__inner){width:100%}

.camera-area {
  position: relative;
  width: 100%;
  height: auto;
  aspect-ratio:4/3;
  background-color: #111827;
  border: 1px solid #dbe3ef;
  border-radius: 14px;
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.video-stream {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
}

.placeholder-camera {
  color: #98a2b3;
}

.button-group {
  display: flex;
  gap: 12px;
  justify-content: flex-start;
  flex-wrap: wrap;
}

.recognition-result {
  width: 100%;
  margin: 24px auto 0;
  text-align: left;
}
.liveness-prompt{position:absolute;left:50%;bottom:22px;transform:translateX(-50%);z-index:3;max-width:88%;padding:10px 16px;border-radius:999px;background:rgba(23,32,51,.88);color:#fff;font-size:14px;font-weight:600;text-align:center;box-shadow:0 8px 24px rgba(0,0,0,.2)}
.quick-options{display:flex;flex-direction:column;align-items:flex-start;padding:12px 14px;margin-bottom:16px;border-radius:10px;background:#f5f8ff}.quick-options small{color:#667085;margin-top:4px}
@media(max-width:950px){.sign-layout{grid-template-columns:1fr;grid-template-areas:"controls" "camera" "actions";max-width:650px;padding:24px 18px;gap:20px}}
@media(max-width:600px){
  .face-recognition :deep(.el-card__header){display:none}
  .face-recognition>.el-card{border:0;box-shadow:none;background:transparent}
  .face-recognition>.el-card :deep(.el-card__body){padding:0}
  .sign-layout{padding:0;gap:14px}
  .eyebrow{font-size:10px}.sign-controls h2{font-size:22px;margin:6px 0}.intro{font-size:13px;line-height:1.55;margin:0 0 14px}
  .step-note{gap:5px;margin-top:8px;font-size:10px;white-space:nowrap}.step-note span{width:22px;height:22px;flex-basis:22px}.step-note i{flex:1;min-width:6px}
  .stage-label{margin-bottom:7px;font-size:11px}.camera-area{aspect-ratio:4/3;max-height:38dvh;border-radius:12px}
  .camera-icon{width:58px;height:58px;margin-bottom:10px}.placeholder-camera h3{font-size:16px}.placeholder-camera p{font-size:11px;margin:5px 0}
  .button-group{display:grid;grid-template-columns:1fr 1.35fr;gap:8px}.button-group .el-button{margin:0;width:100%;padding:8px;font-size:13px}.safe-note{text-align:center;margin-top:8px;font-size:10px}
  .recognition-result{margin-top:10px}
}

.result-header {
  color: #67c23a;
  font-size: 18px;
  font-weight: 600;
  text-align: center;
}
.sign-layout{grid-template-columns:minmax(0,.85fr) minmax(0,1.15fr);gap:20px;padding:16px;max-width:1080px}.camera-area{max-height:42dvh}.video-stream{object-fit:contain}.recognition-result{margin-top:10px}.member-list{max-height:150px;overflow:auto;display:flex;flex-direction:column;width:100%}.intro{margin-bottom:14px}.recognition-result :deep(.el-card__body){padding:8px}.recognition-result :deep(.el-card__header){padding:8px}
@media(max-width:950px) and (min-width:601px){.sign-layout{grid-template-columns:minmax(0,1fr) minmax(0,1fr);grid-template-areas:"controls camera" "actions camera";padding:8px}}
@media(max-width:600px){.sign-layout{grid-template-columns:1fr;padding:0;gap:8px}.intro,.eyebrow,.step-note{display:none}.sign-controls h2{font-size:18px;margin:0 0 8px}.camera-area{max-height:30dvh}.quick-options{padding:6px;margin-bottom:4px}.member-list{max-height:90px}.sign-controls :deep(.el-form-item){margin-bottom:8px}}
.stage-label span.online{background:#12b76a;box-shadow:0 0 0 4px rgba(18,183,106,.12)}
.stage-steps{display:grid;grid-template-columns:repeat(5,1fr);gap:5px;margin-bottom:12px}.stage-steps>div{position:relative;display:flex;align-items:center;justify-content:center;gap:5px;color:#98a2b3;font-size:11px}.stage-steps>div:not(:last-child):after{content:'';position:absolute;right:-7px;width:10px;height:1px;background:#d0d5dd}.stage-steps i{width:20px;height:20px;display:grid;place-items:center;border-radius:50%;background:#f0e9e7;font-style:normal;font-weight:700}.stage-steps .active{color:var(--brand);font-weight:600}.stage-steps .active i{background:var(--brand);color:#fff;box-shadow:0 0 0 4px rgba(177,47,53,.13)}.stage-steps .done{color:#039855}.stage-steps .done i{background:#12b76a;color:#fff}
.face-guide{position:absolute;z-index:2;left:50%;top:50%;width:43%;height:68%;transform:translate(-50%,-50%);border:1px solid rgba(255,255,255,.22);border-radius:46% 46% 42% 42%;transition:.3s}.face-guide.scanning{border-color:rgba(74,222,128,.45);filter:drop-shadow(0 0 10px rgba(34,197,94,.18))}.corner{position:absolute;width:27px;height:27px;border-color:#fff;border-style:solid}.corner.tl{left:-4px;top:-4px;border-width:3px 0 0 3px;border-radius:13px 0 0}.corner.tr{right:-4px;top:-4px;border-width:3px 3px 0 0;border-radius:0 13px 0 0}.corner.bl{left:-4px;bottom:-4px;border-width:0 0 3px 3px;border-radius:0 0 0 13px}.corner.br{right:-4px;bottom:-4px;border-width:0 3px 3px 0;border-radius:0 0 13px}.phase-blink .corner{border-color:#fbbf24}.phase-turn .corner{border-color:#c084fc}.phase-verify .corner{border-color:#60a5fa}.scan-line{position:absolute;left:7%;right:7%;height:2px;background:linear-gradient(90deg,transparent,#4ade80,transparent);box-shadow:0 0 8px #4ade80;animation:scan 1.7s ease-in-out infinite}@keyframes scan{0%,100%{top:12%;opacity:.45}50%{top:86%;opacity:1}}
.live-progress{position:absolute;z-index:4;left:16px;right:16px;top:14px;padding:10px 12px;border-radius:10px;background:rgba(15,23,42,.8);backdrop-filter:blur(7px);color:#fff}.live-progress>div{display:flex;justify-content:space-between;font-size:12px;margin-bottom:7px}.live-progress :deep(.el-progress-bar__outer){background:rgba(255,255,255,.22)}
.success-overlay{position:absolute;inset:0;z-index:6;display:flex;flex-direction:column;align-items:center;justify-content:center;background:linear-gradient(145deg,rgba(4,120,87,.94),rgba(18,183,106,.92));color:#fff}.success-check{width:76px;height:76px;border-radius:50%;display:grid;place-items:center;background:#fff;color:#12a36d;font-size:45px;box-shadow:0 12px 32px rgba(0,0,0,.2);animation:check-pop .55s cubic-bezier(.2,.9,.3,1.35)}.success-overlay h2{font-size:28px;margin:17px 0 7px}.success-overlay p{font-size:17px;margin:0 0 16px}.success-overlay>span{font-size:12px;color:#d1fae5}.success-pop-enter-active{transition:.35s}.success-pop-enter-from{opacity:0;transform:scale(.96)}@keyframes check-pop{0%{transform:scale(.3) rotate(-25deg)}100%{transform:scale(1) rotate(0)}}
.error-guide{display:flex;align-items:center;gap:12px;text-align:left;margin-top:12px;padding:13px 14px;border:1px solid #fecaca;border-radius:11px;background:#fff7f7;color:#d92d20}.error-guide>.el-icon{font-size:24px;flex:none}.error-guide>div{flex:1;display:grid;gap:3px}.error-guide span{font-size:12px;color:#7a271a;line-height:1.45}
@media(max-width:600px){.stage-steps{margin-bottom:7px;gap:2px}.stage-steps>div{font-size:9px;gap:3px}.stage-steps i{width:18px;height:18px;flex:none}.face-guide{width:40%;height:70%}.live-progress{left:8px;right:8px;top:8px;padding:7px 9px}.success-check{width:58px;height:58px;font-size:35px}.success-overlay h2{font-size:22px;margin:11px 0 5px}.success-overlay p{font-size:14px}.error-guide{padding:9px;margin-top:7px}.error-guide span{font-size:11px}}
</style>

