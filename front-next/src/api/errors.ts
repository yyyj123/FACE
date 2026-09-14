interface ErrorResponse {
  status?: number
  data?: unknown
}

interface RequestFailure {
  code?: string
  message?: string
  request?: unknown
  response?: ErrorResponse
}

function messageFromPayload(payload: unknown) {
  if (!payload || typeof payload !== 'object') return ''
  const record = payload as Record<string, unknown>
  for (const field of ['msg', 'message']) {
    const value = record[field]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

export function toRequestError(reason: unknown): Error {
  const failure = (reason && typeof reason === 'object' ? reason : {}) as RequestFailure
  const backendMessage = messageFromPayload(failure.response?.data)
  if (backendMessage) return new Error(backendMessage)

  if (failure.code === 'ECONNABORTED') {
    return new Error('请求超时，请稍后重试')
  }
  if (!failure.response && (failure.request || failure.message === 'Network Error')) {
    return new Error('无法连接服务，请检查网络后重试')
  }

  const status = failure.response?.status
  if (status === 403) return new Error('当前访问地址未获授权，请刷新页面后重试')
  if (status && status >= 500) return new Error('服务暂时不可用，请稍后重试')

  if (reason instanceof Error && reason.message && !reason.message.startsWith('Request failed with status code')) {
    return reason
  }
  return new Error('请求失败，请稍后重试')
}
