import request from './index'
export const getAttendanceSummary=()=>request({url:'/attendance/summary'})

/**
 * 签到
 */
export function checkIn(data) {
  return request({
    url: '/attendance/check-in',
    method: 'post',
    data
  })
}

/**
 * 签退
 */
export function checkOut(attendanceId) {
  return request({
    url: '/attendance/check-out',
    method: 'post',
    params: { attendanceId }
  })
}

/**
 * 获取我的考勤记录
 */
export function getMyAttendance(params) {
  return request({
    url: '/attendance/my',
    method: 'get',
    params
  })
}

/**
 * 获取课程考勤记录
 */
export function getCourseAttendance(courseId, params) {
  return request({
    url: `/attendance/course/${courseId}`,
    method: 'get',
    params
  })
}

/**
 * 获取考勤统计
 */
export function getAttendanceStatistics(courseId, params) {
  return request({
    url: `/attendance/report/${courseId}`,
    method: 'get', params
  })
}

/**
 * 修改考勤状态
 */
export function updateAttendanceStatus(id, status) {
  return request({
    url: `/attendance/${id}`,
    method: 'put',
    params: { status }
  })
}

/**
 * 导出考勤记录
 */
export function exportAttendance(params) {
  return request({
    url: '/attendance/export-xlsx',
    method: 'get',
    params,
    responseType: 'blob'
  })
}

/**
 * 获取考勤记录（分页）
 */
export function getAttendanceRecords(params) {
  return request({
    url: '/attendance/records',
    method: 'get',
    params
  })
}

export function getLeaveRequests() { return request({ url: '/attendance/leaves', method: 'get' }) }
export function submitLeave(params) { return request({ url: '/attendance/leaves', method: 'post', params }) }
export function reviewLeave(id, approved, comment = '') { return request({ url: `/attendance/leaves/${id}/review`, method: 'put', params: { approved, comment } }) }

export const getAttendanceSession=courseId=>request({url:`/attendance/sessions/${courseId}`})
export const startAttendanceSession=(courseId,params)=>request({url:`/attendance/sessions/${courseId}`,method:'post',params})
export const updateAttendanceSession=(courseId,params)=>request({url:`/attendance/sessions/${courseId}`,method:'put',params})
export const cancelAttendanceSession=courseId=>request({url:`/attendance/sessions/${courseId}`,method:'delete'})
export const setStudentClass=(courseId,studentId,className)=>request({url:`/attendance/student-class/${courseId}/${studentId}`,method:'put',params:{className}})
