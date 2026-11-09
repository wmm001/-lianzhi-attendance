import request from './index'

export const getUsers = params => request({ url: '/users', method: 'get', params })
export const createUser = data => request({ url: '/users', method: 'post', data })
export const updateManagedUser = (id, data) => request({ url: `/users/${id}/admin`, method: 'put', data })
export const updateUserStatus = (id, status) => request({ url: `/users/${id}/status`, method: 'put', params: { status } })
export const deleteUser = id => request({ url: `/users/${id}`, method: 'delete' })

// Excel 批量导入：role 为 STUDENT 或 TEACHER，file 为 .xlsx 文件
export const importUsers = (role, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request({ url: '/users/import', method: 'post', params: { role }, data: formData, timeout: 60000 })
}
