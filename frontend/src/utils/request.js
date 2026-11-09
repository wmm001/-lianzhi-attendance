import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 网页开发环境使用 Vite 代理；Android App 使用配置好的服务器地址。
// 真机调试可在登录页设置，例如：http://192.168.1.10:8080/api
const getApiBaseUrl = () => {
  const configured = localStorage.getItem('apiBaseUrl') || import.meta.env.VITE_API_BASE_URL
  if (configured) return configured.replace(/\/$/, '')
  return '/api'
}

const request = axios.create({
  baseURL: getApiBaseUrl(),
  timeout: 10000
})

export const setApiBaseUrl = (url) => {
  const normalized = String(url || '').trim().replace(/\/$/, '')
  if (normalized) localStorage.setItem('apiBaseUrl', normalized)
  else localStorage.removeItem('apiBaseUrl')
  request.defaults.baseURL = normalized || '/api'
}

export const getConfiguredApiBaseUrl = () => request.defaults.baseURL

const getFriendlyMessage = (message = '', errorType = '', details = {}) => {
  const text = String(message)
  if (errorType === 'FACE_NOT_MATCHED' || errorType === 'FACE_IDENTITY_MISMATCH') {
    const confidence = Number(details?.confidence)
    const threshold = Number(details?.threshold)
    const scoreText = Number.isFinite(confidence) && Number.isFinite(threshold)
      ? `（本次相似度 ${confidence.toFixed(3)}，通过阈值 ${threshold.toFixed(2)}）`
      : ''
    return `身份核验失败：检测到的是非注册人脸，与当前账号已注册人脸不匹配。${scoreText}`
  }
  if (errorType === 'NO_REGISTERED_FACE') {
    return '当前账号没有可用的人脸资料，请先在“人脸考勤”页面录入，或联系管理员检查。'
  }
  if (errorType === 'LIVENESS_FAILED') {
    return text || '活体检测未通过，请正对镜头眨眼或张嘴一次后重试。'
  }
  if (errorType === 'FACE_QUALITY_INVALID' || errorType === 'FACE_PROCESSING_FAILED') {
    const reason = text.replace(/^照片不符合拍摄要求[：:]\s*/, '')
    return `照片不符合拍摄要求：${reason}`
  }
  if (/No face detected|clear front-facing photo/i.test(text)) {
    return '照片不符合拍摄要求：未检测到合格的正脸，请将完整脸部置于画面中央后重新拍摄。'
  }
  if (/No matching face|not match|unmatched|非注册人脸|不匹配/i.test(text)) {
    return '身份核验失败：检测到的是非注册人脸，与当前账号已注册人脸不匹配。'
  }
  if (/Recognition service error|feature comparison failed/i.test(text)) {
    return '本次照片无法完成人脸特征比对：请将完整正脸置于画面中央，保持画面清晰并重新拍摄。'
  }
  if (/人脸服务异常.*500 INTERNAL SERVER ERROR/i.test(text)) {
    return '本次人脸识别未能完成，请保持正脸、画面清晰并重新拍摄。'
  }
  if (/人脸服务异常.*400 BAD REQUEST/i.test(text)) {
    return '人脸照片不符合要求，请使用清晰、完整、正面的人脸照片重试。'
  }
  if (/当前不在签到时间内/i.test(text)) {
    return `当前课程暂未开放签到。${text} 请核对课程日期和时间，或选择“全天测试课”进行功能测试。`
  }
  return text || '请求失败'
}

let lastError = { message: '', time: 0 }
const showError = (message) => {
  const friendly = getFriendlyMessage(message)
  const now = Date.now()
  if (lastError.message === friendly && now - lastError.time < 2500) return
  lastError = { message: friendly, time: now }
  ElMessage.error({
    message: friendly,
    duration: 8000,
    showClose: true
  })
}

const clearExpiredSession = config => {
  sessionStorage.removeItem('token')
  localStorage.removeItem('token')
  window.dispatchEvent(new Event('auth:expired'))

  if (!config?.skipAuthRedirect && router.currentRoute.value.path !== '/login') {
    router.replace({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath }
    })
  }
}

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = sessionStorage.getItem('token') || localStorage.getItem('token')
    const isPublicAuthRequest = config.url === '/auth/login'
    if (token && !isPublicAuthRequest) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    if (response.config?.responseType === 'blob') return response.data
    const res = response.data
    if (!res || typeof res !== 'object' || typeof res.code === 'undefined') {
      const server = response.config?.baseURL || request.defaults.baseURL || '未设置'
      const message = `服务器响应格式不正确（${server}）。请在登录页填写电脑地址，例如 http://192.168.1.10:8080/api。`
      if (!response.config?.silent) showError(message)
      const requestError = new Error(message)
      requestError.messageShown = true
      return Promise.reject(requestError)
    }
    if (res.code === 200) {
      return res.data
    } else {
      const errorType = res.data?.errorType
      const message = getFriendlyMessage(res.message, errorType, res.data)
      if (Number(res.code) === 401 && response.config?.url !== '/auth/login') {
        clearExpiredSession(response.config)
        if (!response.config?.silent) ElMessage.error('登录已过期，请重新登录')
      } else if (!response.config?.silent) {
        showError(message)
      }
      const requestError = new Error(message)
      requestError.errorType = errorType
      requestError.details = res.data
      requestError.messageShown = true
      return Promise.reject(requestError)
    }
  },
  error => {
    if (!error.response) {
      const server = error.config?.baseURL || request.defaults.baseURL || '未设置'
      showError(`无法连接服务器（${server}）。手机通过电脑的 5173 端口打开网页时，请在登录页清空“服务器地址”，并确认手机与电脑在可互访的同一网络。`)
    } else if (error.response?.status === 401) {
      if (error.config?.url === '/auth/login') {
        if (!error.config?.silent) showError(error.response?.data?.message || '用户名或密码错误')
      } else {
        clearExpiredSession(error.config)
        if (!error.config?.silent) ElMessage.error('登录已过期，请重新登录')
      }
    } else if (error.response?.status === 403) {
      if (!error.config?.silent) showError('无权访问')
    } else if (error.response?.status === 500) {
      if (!error.config?.silent) showError(error.response?.data?.message || '服务器错误')
    } else {
      if (!error.config?.silent) showError(error.response?.data?.message || error.message || '网络错误')
    }
    error.messageShown = true
    return Promise.reject(error)
  }
)

export default request
