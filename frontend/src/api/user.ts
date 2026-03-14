import request from '@/utils/request'

export const userApi = {
  getList(params: any) {
    return request.get('/users', { params })
  },

  getById(id: number) {
    return request.get(`/users/${id}`)
  },

  create(data: any) {
    return request.post('/users', data)
  },

  update(id: number, data: any) {
    return request.put(`/users/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/users/${id}`)
  },

  resetPassword(id: number, newPassword: string) {
    return request.put(`/users/${id}/password`, { newPassword })
  },

  getCurrentUser() {
    return request.get('/users/me')
  }
}
