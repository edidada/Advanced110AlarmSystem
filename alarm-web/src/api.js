import axios from 'axios'
import { operatorId } from './state'

function uuid() {
  return crypto.randomUUID ? crypto.randomUUID() : `id-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export const api = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

api.interceptors.request.use((config) => {
  const operator = operatorId.value || 'op-web'
  config.headers['X-Operator-Id'] = operator
  config.headers['X-Trace-Id'] = uuid()
  if (['post', 'patch', 'put'].includes(config.method)) {
    config.headers['Idempotency-Key'] = uuid()
  }
  return config
})

/** 统一错误提取：优先服务端 {code,message}，其次 HTTP 状态。 */
export function errText(e) {
  const body = e?.response?.data
  if (body && body.message) {
    return `${body.code || e.response.status} ${body.message}${(body.details || []).join('；')}`
  }
  if (e?.request && !e?.response) return '网络不可达（NodePort/服务不可用？）'
  return e?.message || String(e)
}
