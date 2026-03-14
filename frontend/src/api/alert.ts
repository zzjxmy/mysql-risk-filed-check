import request from '@/utils/request'

export const alertApi = {
  getList(params: any) {
    return request.get('/alerts', { params })
  },

  getById(id: number) {
    return request.get(`/alerts/${id}`)
  },

  create(data: any) {
    return request.post('/alerts', data)
  },

  update(id: number, data: any) {
    return request.put(`/alerts/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/alerts/${id}`)
  },

  test(id: number) {
    return request.post(`/alerts/${id}/test`)
  }
}
