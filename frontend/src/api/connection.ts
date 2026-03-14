import request from '../utils/request'

export interface Connection {
  id?: number
  name: string
  host: string
  port: number
  username: string
  password?: string
  remark?: string
  enabled?: boolean
}

export function getConnections(params?: { name?: string; enabled?: boolean; page?: number; size?: number }) {
  return request.get('/connections', { params })
}

export function getConnection(id: number) {
  return request.get(`/connections/${id}`)
}

export function createConnection(data: Connection) {
  return request.post('/connections', data)
}

export function updateConnection(id: number, data: Connection) {
  return request.put(`/connections/${id}`, data)
}

export function deleteConnection(id: number) {
  return request.delete(`/connections/${id}`)
}

export function testConnection(data: Connection) {
  return request.post('/connections/test', data)
}
