import request from './index'

/**
 * 获取课程列表
 */
export function getCourseList(params) {
  return request({
    url: '/courses',
    method: 'get',
    params
  })
}

/**
 * 获取课程详情
 */
export function getCourseDetail(id) {
  return request({
    url: `/courses/${id}`,
    method: 'get'
  })
}

/**
 * 创建课程
 */
export function createCourse(data) {
  return request({
    url: '/courses',
    method: 'post',
    data
  })
}

/**
 * 更新课程
 */
export function updateCourse(id, data) {
  return request({
    url: `/courses/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除课程
 */
export function deleteCourse(id) {
  return request({
    url: `/courses/${id}`,
    method: 'delete'
  })
}
export const updateCourseStatus = (id,status) => request({url:`/courses/${id}/status`,method:'put',params:{status}})

/**
 * 获取课程学生列表
 */
export function getCourseStudents(id) {
  return request({
    url: `/courses/${id}/students`,
    method: 'get'
  })
}

export function addCourseStudent(id, studentNumber) {
  return request({ url: `/courses/${id}/students`, method: 'post', params: { studentNumber } })
}

/**
 * 获取学校为我安排的课程
 */
export function getEnrolledCourses() {
  return request({
    url: '/courses/my/enrolled',
    method: 'get'
  })
}
