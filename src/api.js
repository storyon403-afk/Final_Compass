import { computed, ref } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const savedSession = localStorage.getItem('finals-compass-session')
let initialSession = { token: '', username: '', displayName: '', role: '', mustChangePassword: false }
if (savedSession) {
  try { initialSession = JSON.parse(savedSession) } catch { localStorage.removeItem('finals-compass-session') }
}
export const authSession = ref(initialSession)
export const authenticated = computed(() => Boolean(authSession.value.token))
export const isAdmin = computed(() => authSession.value.role === 'ADMIN')
export const profile = ref({ publicId: '', nickname: '匿名同学' })

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  if (authSession.value.token) headers.set('Authorization', `Bearer ${authSession.value.token}`)
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    if (response.status === 401 && path !== '/auth/login') clearSession()
    const traceId = response.headers.get('X-Trace-Id')
    const message = body.error || body.detail || `请求失败（${response.status}）`
    const error = new Error(traceId ? `${message}（追踪号：${traceId}）` : message)
    error.status = response.status
    throw error
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') return null
  return response.json()
}

async function requestBlob(path) {
  const headers = new Headers()
  if (authSession.value.token) headers.set('Authorization', `Bearer ${authSession.value.token}`)
  const response = await fetch(`${API_BASE}${path}`, { headers })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    const traceId = response.headers.get('X-Trace-Id')
    const message = body.error || body.detail || `文件请求失败（${response.status}）`
    throw new Error(traceId ? `${message}（追踪号：${traceId}）` : message)
  }
  return response.blob()
}

function saveSession(value) {
  authSession.value = value
  localStorage.setItem('finals-compass-session', JSON.stringify(value))
}

export function clearSession() {
  authSession.value = { token: '', username: '', displayName: '', role: '', mustChangePassword: false }
  profile.value = { publicId: '', nickname: '匿名同学' }
  localStorage.removeItem('finals-compass-session')
}

export const authApi = {
  requestBetaAccess: (email, confirmEmail, phone) => request('/auth/beta-access/request', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, confirmEmail, phone })
  }),
  verifyBetaAccess: (requestId, email, code) => request('/auth/beta-access/verify', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ requestId, email, code })
  }),
  async login(username, password) {
    const session = await request('/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password })
    })
    saveSession(session)
    try {
      await initIdentity()
      return session
    } catch (error) {
      clearSession()
      throw error
    }
  },
  register: (username, password) => request('/auth/register', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password })
  }),
  changePassword: (currentPassword, newPassword) => request('/auth/change-password', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ currentPassword, newPassword })
  }),
  async logout() {
    try { await request('/auth/logout', { method: 'POST' }) } finally { clearSession() }
  }
}

export const browserBridgeApi = {
  bind: () => request('/browser-bridge/bindings', { method: 'POST' })
}

export async function initIdentity() {
  try {
    const identity = await request('/identity/anonymous', { method: 'POST' })
    profile.value = identity
  } catch (error) {
    profile.value = { publicId: '', nickname: authSession.value.displayName || '匿名同学' }
    throw error
  }
  return profile.value
}

export const catalogApi = {
  courses: () => request('/courses'),
  colleges: () => request('/courses/colleges'),
  addCollege: (name) => request('/courses/colleges', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name })
  }),
  addCourse: (fields) => request('/courses', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  teachers: (course = 'data-structure') => request(`/courses/${course}/teachers`),
  addTeacher: (course, fields) => request(`/courses/${course}/teachers`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  })
}

export const circleApi = {
  resources: (course = 'data-structure', teacher = 'lin') => request(`/circles/${course}/${teacher}/resources`),
  discussions: (course = 'data-structure', teacher = 'lin', date = '') => request(`/circles/${course}/${teacher}/discussions${date ? `?date=${encodeURIComponent(date)}` : ''}`),
  guide: (course, teacher) => request(`/circles/${course}/${teacher}/guide`),
  updateGuide: (fields, course, teacher) => request(`/circles/${course}/${teacher}/guide`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  submitGuideReference: (contentMarkdown, course, teacher) => request(`/circles/${course}/${teacher}/guide/submissions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ contentMarkdown })
  }),
  approvedGuideReferences: (course, teacher) => request(`/circles/${course}/${teacher}/guide/submissions`),
  postDiscussion: (content, parentId = null, course = 'data-structure', teacher = 'lin') => request(`/circles/${course}/${teacher}/discussions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, parentId })
  }),
  upload: (fields, course = 'data-structure', teacher = 'lin') => {
    const form = new FormData()
    form.append('title', fields.title)
    form.append('type', fields.type || '同学分享')
    form.append('description', fields.description || '')
    form.append('file', fields.file)
    return request(`/circles/${course}/${teacher}/resources`, { method: 'POST', body: form })
  },
  thank: (resourceId, course, teacher) => request(`/circles/${course}/${teacher}/resources/${resourceId}/thanks`, {
    method: 'POST'
  }),
  file: (resourceId, course, teacher, disposition = 'inline') => requestBlob(`/circles/${course}/${teacher}/resources/${resourceId}/file?disposition=${disposition}`)
}

export const systemApi = {
  modules: () => request('/system/modules'),
  updateModule: (key, fields) => request(`/system/modules/${encodeURIComponent(key)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields) }),
  announcement: () => request('/system/announcement'),
  updateAnnouncement: (content, enabled) => request('/system/announcement', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content, enabled })
  }),
  betaAccessRequests: () => request('/system/beta-access'),
  smtpConfig: () => request('/system/mail/smtp'),
  saveSmtp: (fields) => request('/system/mail/smtp', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  testSmtp: (recipient, adminPassword) => request('/system/mail/smtp/test-and-enable', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ recipient, adminPassword })
  }),
  microsoftMail: () => request('/system/mail/microsoft'),
  authorizeMicrosoftMail: () => request('/system/mail/microsoft/authorize', { method: 'POST' }),
  testMicrosoftMail: (recipient, adminPassword) => request('/system/mail/microsoft/test-and-enable', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ recipient, adminPassword })
  }),
  disconnectMicrosoftMail: (adminPassword) => request('/system/mail/microsoft', {
    method: 'DELETE', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ adminPassword })
  }),
  mailTemplates: () => request('/system/mail/templates'),
  saveMailTemplate: (type, fields) => request(`/system/mail/templates/${encodeURIComponent(type)}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  approveAccess: (id, fields) => request(`/system/mail/beta-access/${id}/approve-and-send`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  suggestDisplayName: () => request('/system/mail/display-name-suggestion'),
  moderation: () => request('/system/moderation'),
  moderate: (type, id, decision) => request(`/system/moderation/${type}/${id}?decision=${decision}`, { method: 'POST' }),
  metrics: () => request('/system/metrics'),
  removeDiscussion: (id) => request(`/system/discussions/${id}`, { method: 'DELETE' })
}

export const mcpAdminApi = {
  overview: () => request('/system/mcp'),
  saveServer: (fields) => request('/system/mcp/servers', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields) }),
  discover: (serverKey) => request(`/system/mcp/servers/${encodeURIComponent(serverKey)}/discover`, { method: 'POST' }),
  diff: (serverKey) => request(`/system/mcp/servers/${encodeURIComponent(serverKey)}/diff`),
  authorize: (serverKey) => request(`/system/mcp/servers/${encodeURIComponent(serverKey)}/oauth/authorize`, { method: 'POST' }),
  disconnectOAuth: (serverKey) => request(`/system/mcp/servers/${encodeURIComponent(serverKey)}/oauth`, { method: 'DELETE' }),
  requestApproval: (fields) => request('/system/mcp/approvals', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  decide: (id, approve, note) => request(`/system/mcp/approvals/${id}/decision`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ approve, note })
  })
}

export const surveyApi = {
  questions: () => request('/survey'),
  submit: (answers, overallSuggestion) => request('/survey/submissions', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ answers, overallSuggestion })
  }),
  adminOverview: () => request('/survey/admin'),
  updateQuestions: (questions) => request('/survey/admin/questions', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ questions })
  })
}

export const aiApi = {
  dashboard: () => request('/ai/dashboard'),
  saveByok: (provider, apiKey, label, consentToStore = true) => request('/ai/byok', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider, apiKey, label, consentToStore })
  }),
  saveReviewByok: (provider, apiKey, label, consentToStore = true) => request('/ai/review-byok', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ provider, apiKey, label, consentToStore })
  }),
  saveVisionByok: (provider, apiKey, label, consentToStore = true) => request('/ai/vision-byok', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ provider, apiKey, label, consentToStore }) }),
  deleteVisionByok: (provider) => request(`/ai/vision-byok/${encodeURIComponent(provider)}`, { method: 'DELETE' }),
  saveVisionFeatures: (fields) => request('/ai/admin/vision-features', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields) }),
  analyzeVision: (file, fields) => { const form=new FormData();form.append('file',file);for(const [key,value] of Object.entries(fields))if(value!==null&&value!==undefined)form.append(key,value);return request('/ai/vision/analyze',{method:'POST',body:form}) },
  deleteByok: (provider) => request(`/ai/byok/${encodeURIComponent(provider)}`, { method: 'DELETE' }),
  savePlatformKey: (provider, model, apiKey, enabled) => request('/ai/admin/platform-key', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider, model, apiKey, enabled })
  }),
  savePlatformDefault: (provider) => request('/ai/admin/platform-default', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ provider })
  }),
  saveInternalTestAccess: (enabled) => request('/ai/admin/internal-test-access', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
  }),
  saveUsagePolicy: (fields) => request('/ai/admin/usage-policy', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  savePlatformReviewKey: (provider, model, apiKey, enabled) => request('/ai/admin/platform-review-key', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider, model, apiKey, enabled })
  }),
  invoke: (fields) => request('/ai/invoke', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  convertAttachment: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request('/ai/attachments/convert', { method: 'POST', body: form })
  }
}

export const aiCenterApi = {
  runtimes: () => request('/ai-center/runtimes'),
  route: (goal, preferredRuntime = 'AUTO', clientCapabilities = []) => request('/ai-center/route', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ goal, preferredRuntime, clientCapabilities })
  }),
  content: (key) => request(`/ai-center/content/${encodeURIComponent(key)}`),
  updateContent: (key, fields) => request(`/ai-center/content/${encodeURIComponent(key)}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  dispatch: (fields) => request('/ai-center/dispatch', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  dispatchRun: (key) => request(`/ai-center/dispatch/${encodeURIComponent(key)}`),
  cancelDispatch: (key) => request(`/ai-center/dispatch/${encodeURIComponent(key)}`, { method: 'DELETE' }),
  dispatchArtifacts: (key) => request(`/ai-center/dispatch/${encodeURIComponent(key)}/artifacts`),
  dispatchArtifactFile: (key, artifactId) => requestBlob(`/ai-center/dispatch/${encodeURIComponent(key)}/artifacts/${artifactId}`),
  reportParticipant: (key, fields) => request(`/ai-center/dispatch/${encodeURIComponent(key)}/participants`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  })
}

export const chatApi = {
  createSession: () => request('/ai-center/chat/sessions', { method: 'POST' }),
  async streamMessages(sessionKey, fields, signal) {
    const headers = new Headers({ 'Content-Type': 'application/json', Accept: 'text/event-stream' })
    if (authSession.value.token) headers.set('Authorization', `Bearer ${authSession.value.token}`)
    const response = await fetch(`${API_BASE}/ai-center/chat/sessions/${encodeURIComponent(sessionKey)}/messages`, {
      method: 'POST', headers, body: JSON.stringify(fields), signal
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      const error = new Error(body.error || body.detail || `Chat 请求失败（${response.status}）`)
      error.status = response.status
      throw error
    }
    return response
  }
}

export const aiFeedbackApi = {
  offer: (fields) => request('/ai-center/feedback/offers', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  submit: (fields) => request('/ai-center/feedback', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  dismiss: (key) => request(`/ai-center/feedback/offers/${encodeURIComponent(key)}`, { method: 'DELETE' }),
  optimizationQueue: () => request('/system/ai-feedback/optimization'),
  decide: (id, status, note) => request(`/system/ai-feedback/optimization/${id}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status, note })
  })
}

export const aiEvolutionApi = {
  dashboard: () => request('/system/ai-evolution'),
  refresh: (date = '') => request(`/system/ai-evolution/refresh${date ? `?date=${encodeURIComponent(date)}` : ''}`, { method: 'POST' }),
  review: (id, status, note) => request(`/system/ai-evolution/recommendations/${id}/review`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status, note })
  })
}

export const cetApi = {
  papers: (level = '') => request(`/cet/papers${level ? `?level=${encodeURIComponent(level)}` : ''}`),
  items: (level, mode, section = '') => request(`/cet/items?level=${encodeURIComponent(level)}&mode=${encodeURIComponent(mode)}${section ? `&section=${encodeURIComponent(section)}` : ''}`),
  createPaper: (fields) => request('/cet/papers', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  updatePaper: (id, fields) => request(`/cet/papers/${id}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  removePaper: (id) => request(`/cet/papers/${id}`, { method: 'DELETE' }),
  paperAsset: (id, type) => requestBlob(`/cet/papers/${id}/assets/${type}`),
  paperAssetUrl: (id, type) => `${API_BASE}/cet/papers/${id}/assets/${type}`,
  createItem: (fields) => request('/cet/items', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  updateItem: (id, fields) => request(`/cet/items/${id}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields)
  }),
  removeItem: (id) => request(`/cet/items/${id}`, { method: 'DELETE' }),
  uploadAudio: (id, file) => {
    const form = new FormData()
    form.append('file', file)
    return request(`/cet/items/${id}/audio`, { method: 'POST', body: form })
  },
  audio: (id) => requestBlob(`/cet/items/${id}/audio`),
  prepareAudioStream: () => request('/auth/stream-cookie', { method: 'POST' }),
  audioUrl: (id) => `${API_BASE}/cet/items/${id}/audio`
}
