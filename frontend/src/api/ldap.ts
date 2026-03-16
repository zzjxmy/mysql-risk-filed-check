import request from '@/utils/request'

export interface LdapConfig {
  id?: number
  enabled?: boolean
  url: string
  baseDn: string
  username?: string
  password?: string
  userSearchBase?: string
  userSearchFilter?: string
  groupSearchBase?: string
  groupSearchFilter?: string
  emailAttribute?: string
  displayNameAttribute?: string
  defaultRole?: string
  description?: string
}

export interface LdapStatus {
  enabled: boolean
}

// 获取LDAP状态（公开接口，登录页面使用）
export function getLdapStatus() {
  return request.get<LdapStatus>('/ldap-config')
}

// 获取LDAP详细配置（需要ADMIN权限）
export function getLdapConfig() {
  return request.get<LdapConfig>('/ldap-config/detail')
}

// 保存LDAP配置
export function saveLdapConfig(data: LdapConfig) {
  return request.post<LdapConfig>('/ldap-config', data)
}

// 测试LDAP连接
export function testLdapConnection(data: LdapConfig) {
  return request.post<boolean>('/ldap-config/test', data)
}
