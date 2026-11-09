import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, logout, getUserInfo } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  // Keep an active login per browser tab. Admin and employee demo tabs must not
  // silently replace each other's request token through shared localStorage.
  const token = ref(sessionStorage.getItem('token') || localStorage.getItem('token') || '')
  if (token.value) sessionStorage.setItem('token', token.value)
  const userInfo = ref(null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')
  const isTeacher = computed(() => userInfo.value?.role === 'TEACHER')
  const isStudent = computed(() => userInfo.value?.role === 'STUDENT')

  function setToken(newToken) {
    token.value = newToken || ''
    if (token.value) {
      sessionStorage.setItem('token', token.value)
      localStorage.setItem('token', token.value)
    } else {
      sessionStorage.removeItem('token')
      localStorage.removeItem('token')
    }
  }

  function setUserInfo(newUserInfo) {
    userInfo.value = newUserInfo
  }

  async function loginAction(username, password) {
    const response = await login(username, password)
    setToken(response.token)
    setUserInfo(response.userInfo)
  }

  function clearSession() {
    token.value = ''
    userInfo.value = null
    sessionStorage.removeItem('token')
    localStorage.removeItem('token')
  }

  async function fetchUserInfo(options = {}) {
    const response = await getUserInfo(options)
    setUserInfo(response)
  }

  function logoutAction() {
    const currentToken = token.value
    clearSession()

    if (currentToken) {
      logout(currentToken).catch(error => {
      console.error('Logout error:', error)
      })
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    isTeacher,
    isStudent,
    setToken,
    setUserInfo,
    clearSession,
    loginAction,
    fetchUserInfo,
    logoutAction
  }
})
