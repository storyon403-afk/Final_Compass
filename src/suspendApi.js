import { authSession } from './api'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'

async function call(path, options = {}) {
  const headers = new Headers(options.headers || {})
  if (authSession.value.token) headers.set('Authorization', `Bearer ${authSession.value.token}`)
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.error || body.detail || `请求失败（${response.status}）`)
  }
  return response.json()
}

export const suspendApi = {
  config: () => call('/suspend/config'),
  admin: () => call('/suspend/admin'),
  update: (settings) => call('/suspend/admin/config', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(settings)
  }),
  upload: (file, durationSeconds) => {
    const form = new FormData()
    form.append('file', file)
    form.append('durationSeconds', String(durationSeconds))
    return call('/suspend/admin/videos', { method: 'POST', body: form })
  },
  async videoUrl(id) {
    const headers = authSession.value.token ? { Authorization: `Bearer ${authSession.value.token}` } : {}
    const response = await fetch(`${API_BASE}/suspend/videos/${id}`, { headers })
    if (!response.ok) throw new Error('暂挂视频载入失败')
    return URL.createObjectURL(await response.blob())
  }
}
