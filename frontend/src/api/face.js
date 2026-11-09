import request from './index'

/**
 * 人脸注册
 */
export function registerFace(formData) {
  return request({
    url: '/face/register',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 人脸验证签到
 */
export function verifyFace(formData) {
  return request({
    url: '/face/verify',
    timeout: 60000,
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function validateLivenessStep(formData) {
  return request({
    url: '/face/liveness/step',
    timeout: 30000,
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 获取人脸特征
 */
export function getFaceFeatures(userId) {
  return request({
    url: `/face/features/${userId}`,
    method: 'get',
    silent: true
  })
}

/**
 * 删除人脸
 */
export function deleteFace(userId) {
  return request({
    url: `/face/${userId}`,
    method: 'delete'
  })
}
export const requestFaceReEnroll = reason => request({ url:'/face/re-enroll/request', method:'post', params:{reason} })
export const getFaceReEnrollRequests = () => request({ url:'/face/re-enroll/requests', method:'get' })
export const approveFaceReEnroll = studentId => request({ url:`/face/re-enroll/${studentId}/approve`, method:'put' })
