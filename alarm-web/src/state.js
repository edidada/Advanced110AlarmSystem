import { reactive, watchEffect } from 'vue'

/** 操作人上下文（模拟认证 X-Operator-Id），localStorage 持久化。 */
const stored = localStorage.getItem('alarm-operator') || 'op-web'
export const operatorId = reactive({ value: stored })

watchEffect(() => {
  localStorage.setItem('alarm-operator', operatorId.value)
})
