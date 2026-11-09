import request from './index'

/**
 * 用户登录
 */
export function login(username, password) {
  return request({
    url: '/auth/login',
    method: 'post',
    data: { username, password }
  })
}

/**
 * 用户注册
 */
export function register(data) {
  return request({
    url: '/auth/register',
    method: 'post',
    data
  })
}

/**
 * 刷新Token
 */
export function refreshToken(refreshToken) {
  return request({
    url: '/auth/refresh',
    method: 'post',
    params: { refreshToken }
  })
}

/**
 * 用户登出
 */
export function logout(token) {
  return request({
    url: '/auth/logout',
    method: 'post',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    silent: true
  })
}

/**
 * 获取当前用户信息
 */
export function getUserInfo(options = {}) {
  return request({
    url: '/users/profile',
    method: 'get',
    ...options
  })
}

/**
 * 修改当前账号密码
 */
export function changePassword(userId, oldPassword, newPassword) {
  return request({
    url: `/users/${userId}/password`,
    method: 'put',
    params: { oldPassword, newPassword }
  })
}
