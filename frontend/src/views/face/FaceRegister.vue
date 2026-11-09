<template>
  <div class="face-register">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>人脸注册</span>
        </div>
      </template>
      <div class="register-content">
        <div v-if="statusLoading" class="status-loading" v-loading="true"></div>

        <div v-else-if="faceRegistered && !reEnrollAllowed" class="registered-panel">
          <el-result icon="success" title="人脸已注册" sub-title="系统已经保存了你的人脸特征，无需重复注册">
            <template #extra>
              <div class="registered-info">
                <p><strong>当前用户：</strong>{{ userStore.userInfo?.realName }}</p>
                <p><strong>学号：</strong>{{ userStore.userInfo?.studentId || '未设置' }}</p>
                <p v-if="registeredAt"><strong>注册时间：</strong>{{ formatTime(registeredAt) }}</p>
              </div>
              <el-button type="primary" plain :disabled="requestPending" :loading="requesting" @click="requestReEnroll">{{requestPending?'申请审核中':'申请重新录入'}}</el-button>
            </template>
          </el-result>
          <el-alert
            title="为防止他人替换人脸，录入后会自动锁定；确需重录时请提交申请，由任课老师批准。"
            type="info"
            :closable="false"
            show-icon
          />
        </div>

        <div v-else class="capture-layout">
        <section class="visual-stage">
        <div class="stage-label"><span class="status-dot"></span>{{ cameraActive ? '摄像头已连接' : '等待采集' }}</div>
        <div class="camera-area">
          <video
            v-if="cameraActive"
            ref="videoElement"
            autoplay
            playsinline
            muted
            class="video-stream"
          ></video>
          <div v-else class="placeholder-camera">
            <div class="camera-icon"><el-icon :size="42"><Camera /></el-icon></div>
            <h3>准备采集人脸</h3><p>开启摄像头后，请将脸部置于取景框中央</p>
          </div>
        </div>

        <div class="preview-area" v-if="capturedImage || imageUrl">
          <div class="preview-title">已选照片预览</div>
          <img :src="capturedImage || imageUrl" class="preview-image" />
        </div>
        </section>

        <aside class="control-panel">
          <span class="eyebrow">FACE ENROLLMENT</span><h2>{{reEnrollAllowed?'重新录入你的正脸':'录入你的正脸'}}</h2>
          <el-alert v-if="reEnrollAllowed" title="老师已批准重录，本次录入成功后将再次锁定。" type="success" :closable="false" show-icon />
          <p class="intro">系统会提取人脸特征用于签到核验，照片本身仅用于本次注册。</p>
          <div class="guide-list"><div><b>01</b><span><strong>正对镜头</strong><small>脸部完整，不要侧脸或遮挡</small></span></div><div><b>02</b><span><strong>保持清晰</strong><small>光线均匀，避免过暗或逆光</small></span></div><div><b>03</b><span><strong>单人入镜</strong><small>画面中请勿出现其他人脸</small></span></div></div>

        <div class="button-group">
          <el-button
            type="primary"
            size="large"
            @click="toggleCamera"
            :loading="cameraLoading"
          >
            {{ cameraActive ? '关闭摄像头' : '开启摄像头' }}
          </el-button>
          <el-button
            type="success"
            size="large"
            :disabled="!cameraActive || !videoReady"
            @click="capturePhoto"
          >
            拍照
          </el-button>
          <el-upload
            :show-file-list="false"
            :before-upload="beforeUpload"
            accept="image/*"
            style="display:inline-block"
          >
            <el-button size="large">上传照片</el-button>
          </el-upload>
        </div>

        <div class="register-button-area" v-if="capturedImage || imageUrl">
          <el-button
            type="primary"
            size="large"
            :disabled="!capturedImage && !imageUrl"
            :loading="loading"
            @click="handleRegister"
            style="width: 200px;"
          >
            注册人脸
          </el-button>
          <el-button size="large" @click="clearImage" v-if="capturedImage || imageUrl">
            清空照片
          </el-button>
        </div>
        <div class="privacy-note">支持 JPG、PNG，文件不超过 5MB</div>
        </aside>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { registerFace, getFaceFeatures, requestFaceReEnroll } from '@/api/face'
import dayjs from 'dayjs'

const userStore = useUserStore()
const videoElement = ref(null)
const cameraActive = ref(false)
const cameraLoading = ref(false)
const loading = ref(false)
const imageUrl = ref('')
const capturedImage = ref('')
const streamRef = ref(null)
const videoReady = ref(false)
const statusLoading = ref(true)
const faceRegistered = ref(false)
const registeredAt = ref('')
const reEnrollAllowed = ref(false)
const requestPending = ref(false)
const requesting = ref(false)

const formatTime = (time) => dayjs(time).format('YYYY-MM-DD HH:mm:ss')

const loadRegistrationStatus = async () => {
  if (!userStore.userInfo?.id) {
    statusLoading.value = false
    return
  }
  statusLoading.value = true
  try {
    const result = await getFaceFeatures(userStore.userInfo.id)
    faceRegistered.value = Boolean(result?.registered)
    reEnrollAllowed.value = Boolean(result?.reEnrollAllowed)
    requestPending.value = Boolean(result?.requestPending)
    registeredAt.value = result?.created_at || result?.registered_at || ''
  } catch (error) {
    console.error('获取人脸注册状态失败:', error)
    faceRegistered.value = false
  } finally {
    statusLoading.value = false
  }
}

const toggleCamera = async () => {
  if (cameraActive.value) {
    stopCamera()
  } else {
    await startCamera()
  }
}

const startCamera = async () => {
  cameraLoading.value = true
  videoReady.value = false
  try {
    console.log('正在请求摄像头权限...')
    const stream = await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: 640 },
        height: { ideal: 480 },
        facingMode: 'user'
      }
    })
    console.log('摄像头权限获取成功')

    streamRef.value = stream
    // 先切换状态，让 v-if 创建 video 元素，再绑定媒体流。
    cameraActive.value = true
    await nextTick()
    console.log('videoElement:', videoElement.value)

    const video = videoElement.value
    if (!video) {
      throw new Error('摄像头画面组件初始化失败')
    }

    video.srcObject = stream
    await new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => reject(new Error('摄像头画面加载超时')), 8000)
      video.onloadedmetadata = () => {
        window.clearTimeout(timer)
        resolve()
      }
      video.onerror = () => {
        window.clearTimeout(timer)
        reject(new Error('摄像头画面播放失败'))
      }
    })
    await video.play()
    videoReady.value = video.videoWidth > 0 && video.videoHeight > 0
    if (!videoReady.value) {
      throw new Error('摄像头没有输出有效画面')
    }
    console.log('视频加载完成，尺寸:', video.videoWidth, 'x', video.videoHeight)
    ElMessage.success('摄像头已开启')
  } catch (error) {
    stopCamera()
    console.error('无法访问摄像头:', error)
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

const stopCamera = () => {
  videoReady.value = false
  if (videoElement.value) {
    videoElement.value.pause()
    videoElement.value.srcObject = null
  }
  if (streamRef.value) {
    streamRef.value.getTracks().forEach(track => track.stop())
    streamRef.value = null
  }
  cameraActive.value = false
}

const capturePhoto = () => {
  if (!videoElement.value || !videoReady.value) {
    ElMessage.warning('摄像头画面尚未准备好')
    return
  }

  const video = videoElement.value
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  const ctx = canvas.getContext('2d')

  ctx.drawImage(video, 0, 0)
  capturedImage.value = canvas.toDataURL('image/jpeg', 0.8)

  ElMessage.success('照片拍摄成功')
}

const beforeUpload = (file) => {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过5MB')
    return false
  }

  const reader = new FileReader()
  reader.readAsDataURL(file)
  reader.onload = () => {
    imageUrl.value = reader.result
  }
  return false
}

const clearImage = () => {
  imageUrl.value = ''
  capturedImage.value = ''
}

const handleRegister = async () => {
  if (!userStore.userInfo?.id) {
    ElMessage.error('未获取到用户信息')
    return
  }

  // 将 base64 转换为 Blob
  const photoData = capturedImage.value || imageUrl.value
  if (!photoData) {
    ElMessage.error('请先拍照或上传照片')
    return
  }

  loading.value = true
  try {
    const response = await fetch(photoData)
    const blob = await response.blob()
    const file = new File([blob], 'face.jpg', { type: 'image/jpeg' })

    const formData = new FormData()
    formData.append('faceImage', file)
    formData.append('studentId', userStore.userInfo.id)

    await registerFace(formData)

    faceRegistered.value = true
    reEnrollAllowed.value = false
    requestPending.value = false
    registeredAt.value = new Date().toISOString()
    await userStore.fetchUserInfo({ silent: true }).catch(() => {})
    ElMessage.success({ message: '人脸注册成功，已保存人脸特征！', duration: 4000 })
    clearImage()
    stopCamera()
  } catch (error) {
    console.error('注册失败:', error)
    if (!error.messageShown) {
      ElMessage.error({ message: error.message || '注册失败', duration: 8000, showClose: true })
    }
  } finally {
    loading.value = false
  }
}

const requestReEnroll = async () => {
  try {
    const {value}=await ElMessageBox.prompt('请简要说明需要重新录入的原因','申请重新录入',{inputPlaceholder:'例如：外貌变化、原录入不清晰',inputValidator:v=>!!v?.trim()||'请填写申请原因'})
    requesting.value = true
    await requestFaceReEnroll(value.trim())
    requestPending.value = true
    ElMessage.success('申请已提交，请等待任课老师批准')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      if(!error.messageShown) ElMessage.error(error.message||'申请提交失败')
    }
  } finally {
    requesting.value = false
  }
}

onMounted(() => {
  loadRegistrationStatus()
})

onUnmounted(() => {
  stopCamera()
})
</script>

<style scoped>
.face-register {
  padding: 0;
}

.card-header {
  font-weight: bold;
  font-size: 16px;
}

.register-content {
  padding: 10px 0;
}
.capture-layout{display:grid;grid-template-columns:minmax(420px,1.15fr) minmax(320px,.85fr);max-width:1040px;margin:0 auto;gap:54px;align-items:center;padding:34px 30px 42px}.visual-stage{position:relative}.stage-label{display:inline-flex;align-items:center;gap:8px;font-size:13px;color:#706668;margin-bottom:12px}.status-dot{width:8px;height:8px;border-radius:50%;background:#98a2b3}.camera-icon{width:76px;height:76px;display:grid;place-items:center;border-radius:20px;background:rgba(255,255,255,.08);color:#d0d5dd;margin:0 auto 18px}.placeholder-camera h3{color:#f2f4f7;font-size:18px;margin:0 0 8px}.placeholder-camera p{color:#98a2b3;font-size:13px}.control-panel{text-align:left}.eyebrow{font-size:12px;font-weight:700;letter-spacing:1.5px;color:var(--brand)}.control-panel h2{font-size:26px;margin:10px 0;color:var(--text)}.intro{color:#706668;line-height:1.7;margin-bottom:24px}.guide-list{display:flex;flex-direction:column;gap:15px;margin-bottom:26px}.guide-list>div{display:flex;gap:13px;align-items:center}.guide-list b{width:34px;height:34px;display:grid;place-items:center;background:var(--brand-soft);color:var(--brand);border-radius:9px;font-size:12px}.guide-list span{display:flex;flex-direction:column;gap:3px}.guide-list strong{font-size:14px;color:#44393b}.guide-list small{font-size:12px;color:#98a2b3}.privacy-note{font-size:12px;color:#98a2b3;margin-top:14px}

.status-loading {
  min-height: 320px;
}

.registered-panel {
  max-width: 560px;
  margin: 0 auto;
}

.registered-info {
  margin-bottom: 18px;
  color: #606266;
  text-align: left;
}

.registered-info p {
  margin: 8px 0;
}

.registered-panel .el-alert {
  margin-top: 20px;
  text-align: left;
}

.camera-area {
  width: 100%;
  height: auto;
  aspect-ratio: 4 / 3;
  background-color: #111827;
  border: 1px solid #dbe3ef;
  border-radius: 14px;
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}

.video-stream {
  width: 100%;
  height: 100%;
  object-fit: contain;
  transform: scaleX(-1); /* 镜像翻转 */
}

.placeholder-camera {
  color: #999;
  text-align: center;
}

.placeholder-camera p {
  margin-top: 10px;
}

.preview-area {
  width: 100%;
  margin: 14px auto 0;
  text-align: center;
}

.preview-title {
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
}

.preview-image {
  width: 178px;
  height: 178px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid var(--el-color-primary);
}

.button-group {
  display: flex;
  gap: 10px;
  justify-content: flex-start;
  margin-bottom: 20px;
}

.register-button-area {
  display: flex;
  gap: 10px;
  justify-content: flex-start;
}
@media(max-width:1000px){.capture-layout{grid-template-columns:1fr;gap:30px;max-width:650px;padding:20px}.control-panel{order:-1}.guide-list{display:grid;grid-template-columns:repeat(3,1fr)}.guide-list>div{align-items:flex-start}.guide-list small{display:none}}
@media(max-width:600px){.capture-layout{padding:8px 0}.guide-list{grid-template-columns:1fr}.guide-list small{display:block}.button-group,.register-button-area{flex-wrap:wrap}.button-group>*{margin-left:0!important}}
</style>
