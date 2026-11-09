import request from './index'

export const getDashboard = () => request({ url: '/enterprise/dashboard' })
export const getAbsencePredictions = () => request({ url: '/enterprise/absence-predictions' })
export const getMe = () => request({ url: '/enterprise/me' })
export const getOffices = () => request({ url: '/enterprise/offices' })
export const createOffice = data => request({ url: '/enterprise/offices', method: 'post', data })
export const updateOffice = (id, data) => request({ url: `/enterprise/offices/${id}`, method: 'put', data })
export const getEmployees = () => request({ url: '/enterprise/employees' })
export const assignOffice = (userId, officeId) => request({ url: `/enterprise/employees/${userId}/office`, method: 'put', data: { officeId } })
export const createEmployee = data => request({ url: '/users', method: 'post', data: { ...data, role: 'STUDENT' } })
export const updateEmployee = (id, data) => request({ url: `/users/${id}/admin`, method: 'put', data: { ...data, role: 'STUDENT' } })
export const setEmployeeStatus = (id, status) => request({ url: `/users/${id}/status`, method: 'put', params: { status } })
export const getQr = officeId => request({ url: `/enterprise/qr/${officeId}` })
export const qrCheckIn = token => request({ url: '/enterprise/qr/check-in', method: 'post', data: { token } })
export const faceRegister = image => {
  const data = new FormData(); data.append('faceImage', image, 'face.jpg')
  return request({ url: '/enterprise/face/register', method: 'post', data })
}
export const faceCheckIn = (image, frames = [], requireLiveness = false) => {
  const data = new FormData(); data.append('faceImage', image, 'face.jpg')
  data.append('requireLiveness', String(requireLiveness))
  frames.forEach((frame, index) => data.append('livenessFrames', frame, `frame-${index}.jpg`))
  return request({ url: '/enterprise/face/check-in', method: 'post', data, timeout: 60000 })
}
export const resetFace = id => request({ url: `/enterprise/face/${id}`, method: 'delete' })
export const checkOut = () => request({ url: '/enterprise/check-out', method: 'post' })
export const getRecords = params => request({ url: '/enterprise/records', params })
export const manualCheckIn = data => request({ url: '/enterprise/manual-check-in', method: 'post', data })
export const askAttendanceAgent = data => request({ url: '/enterprise/agent/chat', method: 'post', data, timeout: 20000 })
