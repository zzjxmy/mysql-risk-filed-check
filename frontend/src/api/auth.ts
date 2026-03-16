import request from '../utils/request'

export interface LoginResponse {
  code: number
  message: string
  data: {
    token: string
    tokenType: string
    userId: number
    username: string
    nickname: string
    role: string
  }
}

export function login(username: string, password: string, useLdap: boolean = false): Promise<LoginResponse> {
  return request.post('/auth/login', { username, password, useLdap })
}

export function logout() {
  return request.post('/auth/logout')
}

export function getCurrentUser() {
  return request.get('/auth/me')
}
