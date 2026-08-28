const API_BASE = import.meta.env.VITE_API_BASE || '/api'

async function call(path, options = {}) {
  const headers = new Headers(options.headers || {})
  if (!['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase())) {
    const csrf = document.cookie.split('; ').find(item => item.startsWith('finals_compass_csrf='))?.split('=').slice(1).join('=')
    if (csrf) headers.set('X-CSRF-Token', decodeURIComponent(csrf))
  }
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers, credentials: 'include' })
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
    const response = await fetch(`${API_BASE}/suspend/videos/${id}`, { credentials: 'include' })
    if (!response.ok) throw new Error('暂挂视频载入失败')
    return URL.createObjectURL(await response.blob())
  }
}
