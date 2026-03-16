import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin } from '../api/auth'

interface UserInfo {
  userId: number
  username: string
  nickname: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed(() => !!token.value)

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  const setUserInfo = (info: UserInfo) => {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  const login = async (username: string, password: string, useLdap: boolean = false) => {
    const res = await apiLogin(username, password, useLdap)
    if (res.code === 200) {
      setToken(res.data.token)
      setUserInfo({
        userId: res.data.userId,
        username: res.data.username,
        nickname: res.data.nickname,
        role: res.data.role
      })
      return true
    }
    throw new Error(res.message)
  }

  const logout = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    setToken,
    setUserInfo,
    login,
    logout
  }
})
