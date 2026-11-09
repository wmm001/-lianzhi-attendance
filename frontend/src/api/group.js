import request from './index'
export const getMyGroup = () => request({ url: '/groups/mine', method: 'get' })
export const getCourseGroups = courseId => request({url:`/groups/course/${courseId}`})
export const createGroup = (courseId,name,leaderId) => request({ url: '/groups', method: 'post', params: {courseId,name,leaderId} })
export const addGroupMember = (groupId, studentId) => request({ url: `/groups/${groupId}/members`, method: 'post', params: { studentId } })
export const removeGroupMember = (groupId, studentId) => request({ url: `/groups/${groupId}/members/${studentId}`, method: 'delete' })
export const setGroupLeader = (groupId,studentId) => request({url:`/groups/${groupId}/leader/${studentId}`,method:'put'})
export const deleteGroup = groupId => request({url:`/groups/${groupId}`,method:'delete'})
