const PROVIDERS = {
  KIMI: { url: 'https://kimi.moonshot.cn/', roles: '请提出完整方案并给出可靠依据。' },
  DEEPSEEK: { url: 'https://chat.deepseek.com/', roles: '请独立分析，重点检查逻辑、事实和遗漏。' },
  QWEN: { url: 'https://www.qianwen.com/', roles: '请提出改进建议和更优表达。' }
}
const EDITORS = ['textarea', '[contenteditable="true"]', '[contenteditable="plaintext-only"]', '[role="textbox"]', '[data-placeholder]', '.ql-editor', '.ProseMirror']
const SEND_BUTTONS = ['button[type="submit"]', '[data-testid*="send"]', 'button[aria-label*="发送"]', 'button[aria-label*="Send"]', 'button[class*="send"]', '[class*="send-btn"]']
const ANSWERS = ['[data-message-author-role="assistant"]', '.markdown', '.message-content', '[class*="answer"]', '[class*="markdown"]']
let resumingLoginRuns = false
const cancelledRuns = new Set()
let platformTabId = null

chrome.runtime.onMessage.addListener((message, sender) => {
  if (message?.type === 'REGISTER_PLATFORM_TAB' && sender.tab?.id) platformTabId = sender.tab.id
  if (message?.type === 'START_RUN') run(message, sender.tab?.id).catch(error => notify(sender.tab?.id, { provider: 'PLATFORM', status: 'FAILED', errorCode: 'EXTENSION_ERROR', message: error.message }))
  if (message?.type === 'STOP_RUN') cancelMultiWebRun(message.runKey).catch(() => {})
  if (message?.type === 'START_AGENT_BROWSER') agentResearch(message, sender.tab?.id).catch(error => notifyAgent(sender.tab?.id, { requestId: message.requestId, status: 'FAILED', message: error.message }))
})

async function cancelMultiWebRun(runKey) {
  cancelledRuns.add(runKey)
  const { multiWebRuns = {} } = await chrome.storage.local.get('multiWebRuns'), state = multiWebRuns[runKey]
  if (state) {
    state.cancelled = true; state.phase = 'CANCELLED'
    await closeTaskTabsAndReturn(state)
    delete multiWebRuns[runKey]
    await chrome.storage.local.set({ multiWebRuns })
  }
}

async function agentResearch(message, appTabId) {
  const tab = await chrome.tabs.create({ url: `https://www.bing.com/search?q=${encodeURIComponent(message.goal)}`, active: false })
  await waitLoaded(tab.id)
  const search = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: collectSearchResults })
  const links = (search[0]?.result || []).slice(0, 3), pages = []
  for (const item of links) {
    try {
      await chrome.tabs.update(tab.id, { url: item.url, active: false }); await waitLoaded(tab.id)
      const extracted = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: extractReadablePage })
      pages.push({ title: item.title, url: item.url, text: extracted[0]?.result || '' })
    } catch (error) { pages.push({ title: item.title, url: item.url, error: error.message }) }
  }
  await chrome.tabs.remove(tab.id).catch(() => {})
  notifyAgent(appTabId, { requestId: message.requestId, status: 'COMPLETED', result: { query: message.goal, pages } })
}

function collectSearchResults() {
  return [...document.querySelectorAll('li.b_algo h2 a, #search a')].map(a => ({ title: a.textContent?.trim(), url: a.href })).filter(x => x.title && /^https?:/.test(x.url)).slice(0, 6)
}

function extractReadablePage() {
  const root = document.querySelector('main, article, [role="main"]') || document.body, clone = root.cloneNode(true)
  clone.querySelectorAll('script,style,nav,footer,header,aside,form,button,input,textarea').forEach(node => node.remove())
  return (clone.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 16000)
}

function notifyAgent(tabId, payload) {
  if (tabId) chrome.tabs.sendMessage(tabId, { type: 'AGENT_BROWSER_RESULT', ...payload }).catch(() => {})
  chrome.storage.local.set({ lastStatus: { provider: 'AGENT_BROWSER', ...payload } })
}

async function run(message, appTabId) {
  const participants = message.participants?.length ? message.participants : Object.keys(PROVIDERS).map(providerKey => ({ providerKey }))
  const assignments = Object.fromEntries(participants.map(item => {
    const key = item.providerKey || item.provider_key
    return [key, item.assignment || message.goal]
  }))
  const state = { runKey: message.runKey, goal: message.goal, appTabId, participants, assignments, tabs: {}, results: {}, providerStatus: {}, phase: 'INITIAL' }
  await saveRun(state)
  const initial = await Promise.all(participants.map(participant => {
    const providerKey = participant.providerKey || participant.provider_key
    return execute(state, providerKey, assignments[providerKey])
  }))
  if (initial.some(item => item?.status === 'LOGIN_REQUIRED')) return
  await finishRun(state)
}

async function execute(state, providerKey, assignment, existingTabId = null) {
  if (state.cancelled || cancelledRuns.has(state.runKey)) return { status: 'CANCELLED' }
  const provider = PROVIDERS[providerKey]
  if (!provider) return notify(state.appTabId, { runKey: state.runKey, provider: providerKey, status: 'FAILED', errorCode: 'UNSUPPORTED_PROVIDER' })
  let tab
  try { tab = existingTabId ? await chrome.tabs.get(existingTabId) : await chrome.tabs.create({ url: provider.url, active: false }) }
  catch { tab = await chrome.tabs.create({ url: provider.url, active: false }) }
  state.tabs[providerKey] = tab.id
  state.providerStatus ||= {}
  await saveRun(state)
  await waitLoaded(tab.id)
  const inspected = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: inspectAndSubmit, args: [`${provider.roles}\n\n请完成以下任务：\n${assignment}`, EDITORS, SEND_BUTTONS] })
  const outcome = inspected[0]?.result || { status: 'FAILED', errorCode: 'PAGE_UNAVAILABLE' }
  if (outcome.status === 'LOGIN_REQUIRED') {
    state.phase = 'WAITING_LOGIN'
    state.providerStatus[providerKey] = 'LOGIN_REQUIRED'
    await saveRun(state)
    await chrome.tabs.update(tab.id, { active: true })
  }
  if (outcome.status === 'RUNNING') state.providerStatus[providerKey] = 'RUNNING'
  else if (outcome.status !== 'LOGIN_REQUIRED') state.providerStatus[providerKey] = outcome.status
  await saveRun(state)
  notify(state.appTabId, { runKey: state.runKey, provider: providerKey, tabId: tab.id, phase: 'INITIAL', ...outcome })
  if (outcome.status !== 'RUNNING') return
  const result = await pollAnswer(tab.id, outcome.before || '')
  if (state.cancelled || cancelledRuns.has(state.runKey)) return { status: 'CANCELLED' }
  if (result.status === 'COMPLETED') state.results[providerKey] = result.result
  state.providerStatus[providerKey] = result.status
  await saveRun(state)
  notify(state.appTabId, { runKey: state.runKey, provider: providerKey, tabId: tab.id, phase: 'INITIAL', ...result })
  return result
}

async function finishRun(state) {
  if (state.cancelled || cancelledRuns.has(state.runKey)) return
  const completed = Object.entries(state.results).filter(([, value]) => value)
  if (!completed.length) return
  state.phase = 'COMPLETED'
  await saveRun(state)
  await closeTaskTabsAndReturn(state)
  notify(state.appTabId, { runKey: state.runKey, provider: 'PLATFORM', phase: 'BATCH', status: 'COMPLETED', result: Object.fromEntries(completed) })
}

async function closeTaskTabsAndReturn(state) {
  const taskTabIds = [...new Set(Object.values(state.tabs || {}).filter(id => id && id !== state.appTabId))]
  for (const tabId of taskTabIds) await chrome.tabs.remove(tabId).catch(() => {})
  state.tabs = {}
  if (!state.appTabId) return
  const appTab = await chrome.tabs.get(state.appTabId).catch(() => null)
  if (!appTab) return
  await chrome.windows.update(appTab.windowId, { focused: true }).catch(() => {})
  await chrome.tabs.update(appTab.id, { active: true }).catch(() => {})
}

async function saveRun(state) {
  if (cancelledRuns.has(state.runKey)) return
  const { multiWebRuns = {} } = await chrome.storage.local.get('multiWebRuns')
  multiWebRuns[state.runKey] = state
  await chrome.storage.local.set({ multiWebRuns })
}

async function resumeLoginRuns() {
  if (resumingLoginRuns) return
  resumingLoginRuns = true
  try {
  const { multiWebRuns = {} } = await chrome.storage.local.get('multiWebRuns')
  for (const state of Object.values(multiWebRuns)) {
    if (state.phase !== 'WAITING_LOGIN') continue
    state.providerStatus ||= {}
    for (const participant of state.participants) {
      const key = participant.providerKey || participant.provider_key
      if (state.providerStatus[key] !== 'LOGIN_REQUIRED') continue
      await execute(state, key, state.assignments[key], state.tabs[key]).catch(() => ({ status: 'LOGIN_REQUIRED' }))
    }
    const statuses = state.participants.map(item => state.providerStatus[item.providerKey || item.provider_key])
    if (statuses.every(status => status === 'COMPLETED' || status === 'FAILED')) await finishRun(state)
  }
  } finally { resumingLoginRuns = false }
}

async function waitLoaded(tabId) {
  for (let i = 0; i < 60; i++) {
    const tab = await chrome.tabs.get(tabId)
    if (tab.status === 'complete') return
    await new Promise(resolve => setTimeout(resolve, 500))
  }
}

async function pollAnswer(tabId, before) {
  let last = '', stable = 0
  for (let i = 0; i < 120; i++) {
    await new Promise(resolve => setTimeout(resolve, 1500))
    try {
      const result = await chrome.scripting.executeScript({ target: { tabId }, func: readAnswer, args: [ANSWERS] })
      const text = result[0]?.result || ''
      if (text && text !== before && text === last) stable++
      else stable = 0
      last = text
      if (stable >= 4) return { status: 'COMPLETED', result: text }
    } catch (error) {
      return { status: 'FAILED', errorCode: 'TAB_CLOSED', message: error.message }
    }
  }
  return { status: 'FAILED', errorCode: 'RESPONSE_TIMEOUT', result: last }
}

function notify(tabId, payload) {
  if (tabId) chrome.tabs.sendMessage(tabId, { type: 'PARTICIPANT_STATUS', ...payload }).catch(() => {})
  chrome.storage.local.set({ lastStatus: payload })
}

async function inspectAndSubmit(prompt, editorSelectors, sendSelectors) {
  const visible = element => element && element.getClientRects().length > 0
  let editor = null, loginSeen = false
  // Qianwen mounts its editor after the document load event. Wait for the SPA
  // to hydrate instead of doing a single, racy querySelector pass.
  for (let attempt = 0; attempt < 80; attempt++) {
    editor = editorSelectors.map(selector => [...document.querySelectorAll(selector)].find(visible)).find(Boolean)
    if (editor) break
    const body = document.body?.innerText || ''
    loginSeen ||= /登录|注册|sign\s*in|log\s*in|验证码|扫码登录/i.test(body)
    if (loginSeen && attempt >= 8) return { status: 'LOGIN_REQUIRED', errorCode: 'USER_LOGIN_REQUIRED' }
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  if (!editor) return { status: 'FAILED', errorCode: 'EDITOR_NOT_FOUND' }
  const before = [...document.querySelectorAll('[class*="answer"], [class*="markdown"], .message-content')].at(-1)?.innerText || ''
  editor.focus()
  if (editor instanceof HTMLTextAreaElement || editor instanceof HTMLInputElement) {
    const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(editor), 'value')?.set
    setter ? setter.call(editor, prompt) : editor.value = prompt
  } else {
    editor.focus()
    const selection = window.getSelection(), range = document.createRange()
    range.selectNodeContents(editor); selection.removeAllRanges(); selection.addRange(range)
    if (!document.execCommand('insertText', false, prompt)) editor.textContent = prompt
  }
  editor.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, inputType: 'insertText', data: prompt }))
  editor.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: prompt }))
  editor.dispatchEvent(new Event('change', { bubbles: true }))
  await new Promise(resolve => setTimeout(resolve, 350))
  const written = editor instanceof HTMLTextAreaElement || editor instanceof HTMLInputElement ? editor.value : editor.innerText || editor.textContent || ''
  if (!written.trim()) return { status: 'FAILED', errorCode: 'INPUT_REJECTED' }
  const button = sendSelectors.map(selector => [...document.querySelectorAll(selector)].find(visible)).find(Boolean)
  if (button && !button.disabled) button.click()
  else {
    editor.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }))
    editor.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }))
  }
  return { status: 'RUNNING', before }
}

function readAnswer(selectors) {
  const candidates = selectors.flatMap(selector => [...document.querySelectorAll(selector)]).filter(element => element.getClientRects().length > 0)
  return candidates.at(-1)?.innerText?.trim() || ''
}

// ---- Backend WebSocket bridge (Agent Gateway browser control) ----
const BRIDGE_DEFAULT_URL = 'ws://127.0.0.1:8080/ws/browser-bridge'
let bridgeSocket = null, bridgePingTimer = null, bridgeReconnectTimer = null, bridgeTabId = null

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === 'local' && (changes.bridgeConfig || changes.bridgeEnabled)) scheduleBridge(true)
})
chrome.alarms.create('bridge-keepalive', { periodInMinutes: 0.4 })
chrome.alarms.create('multiweb-resume', { periodInMinutes: 0.25 })
chrome.alarms.onAlarm.addListener(alarm => {
  if (alarm.name === 'bridge-keepalive') scheduleBridge(false)
  if (alarm.name === 'multiweb-resume') resumeLoginRuns().catch(() => {})
})

async function bridgeConfig() {
  const { bridgeConfig: config, bridgeEnabled } = await chrome.storage.local.get(['bridgeConfig', 'bridgeEnabled'])
  return { enabled: Boolean(bridgeEnabled), url: config?.url || BRIDGE_DEFAULT_URL, token: config?.token || '' }
}

async function scheduleBridge(restart) {
  const config = await bridgeConfig()
  if (!config.enabled || !config.token) { await setBridgeStatus('IDLE', '未启用或未配置 Token'); return closeBridge() }
  if (bridgeSocket && (bridgeSocket.readyState === WebSocket.OPEN || bridgeSocket.readyState === WebSocket.CONNECTING)) {
    if (!restart) return
    await closeBridge()
  }
  connectBridge(config)
}

function connectBridge(config) {
  try { bridgeSocket = new WebSocket(`${config.url}?token=${encodeURIComponent(config.token)}`) }
  catch (error) { setBridgeStatus('ERROR', error.message); return retryBridge() }
  bridgeSocket.onopen = () => {
    setBridgeStatus('CONNECTED', '已连接后端')
    bridgePingTimer = setInterval(() => sendBridge({ type: 'PING' }), 20000)
  }
  bridgeSocket.onmessage = event => {
    let message; try { message = JSON.parse(event.data) } catch { return }
    if (message.type === 'COMMAND') executeBridgeCommand(message).catch(error => sendResult(message.commandId, 'FAILED', null, error.message))
  }
  bridgeSocket.onclose = () => { setBridgeStatus('DISCONNECTED', '连接断开，稍后重试'); retryBridge() }
  bridgeSocket.onerror = () => {}
}

function retryBridge() {
  clearBridgeTimers()
  if (bridgeReconnectTimer) return
  bridgeReconnectTimer = setTimeout(async () => { bridgeReconnectTimer = null; scheduleBridge(false) }, 5000)
}

async function closeBridge() {
  clearBridgeTimers()
  if (bridgeSocket) { try { bridgeSocket.close() } catch {} bridgeSocket = null }
}

function clearBridgeTimers() {
  if (bridgePingTimer) clearInterval(bridgePingTimer)
  bridgePingTimer = null
}

function sendBridge(payload) {
  if (bridgeSocket && bridgeSocket.readyState === WebSocket.OPEN) bridgeSocket.send(JSON.stringify(payload))
}

function sendResult(commandId, status, result, error) {
  sendBridge({ type: 'RESULT', commandId, status, result: result ?? null, error: error || undefined })
}

function setBridgeStatus(status, detail) {
  chrome.storage.local.set({ bridgeStatus: { status, detail, at: new Date().toISOString() } })
}

async function executeBridgeCommand(message) {
  const { commandId, command, params = {} } = message
  try {
    if (command === 'navigate') return sendResult(commandId, 'COMPLETED', await bridgeNavigate(params))
    if (command === 'get_content') return sendResult(commandId, 'COMPLETED', await bridgeGetContent(params))
    if (command === 'get_links') return sendResult(commandId, 'COMPLETED', await bridgeGetLinks(params))
    if (command === 'finish_research') return sendResult(commandId, 'COMPLETED', await bridgeFinishResearch(params))
    if (command === 'click') return sendResult(commandId, 'COMPLETED', await bridgeAct(params, 'click'))
    if (command === 'type') return sendResult(commandId, 'COMPLETED', await bridgeAct(params, 'type'))
    if (command === 'screenshot') return sendResult(commandId, 'COMPLETED', await bridgeScreenshot(params))
    if (command === 'wait') { await new Promise(resolve => setTimeout(resolve, Math.min(Number(params.ms) || 1000, 30000))); return sendResult(commandId, 'COMPLETED', { waitedMs: Number(params.ms) || 1000 }) }
    sendResult(commandId, 'FAILED', null, `不支持的命令：${command}`)
  } catch (error) {
    sendResult(commandId, 'FAILED', null, error.message)
  }
}

async function targetTab(params) {
  const tabId = params.tabId || bridgeTabId
  if (tabId) { try { return await chrome.tabs.get(tabId) } catch {} }
  throw new Error('没有可用的标签页，请先执行 navigate')
}

async function bridgeNavigate(params) {
  if (!params.url || !/^https?:/i.test(params.url)) throw new Error('navigate 需要 http/https URL')
  if (bridgeTabId) { try { await chrome.tabs.update(bridgeTabId, { url: params.url, active: true }) } catch { bridgeTabId = null } }
  if (!bridgeTabId) bridgeTabId = (await chrome.tabs.create({ url: params.url, active: true })).id
  await waitLoaded(bridgeTabId)
  const tab = await chrome.tabs.get(bridgeTabId)
  return { tabId: bridgeTabId, url: tab.url, title: tab.title }
}

async function bridgeGetContent(params) {
  const tab = await targetTab(params)
  const extracted = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: extractReadablePage })
  return { tabId: tab.id, url: tab.url, title: tab.title, text: extracted[0]?.result || '' }
}

async function bridgeGetLinks(params) {
  const tab = await targetTab(params)
  const extracted = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: extractPageLinks, args: [Number(params.limit) || 12] })
  return { tabId: tab.id, url: tab.url, title: tab.title, links: extracted[0]?.result || [] }
}

async function bridgeFinishResearch(params) {
  const researchTabId = bridgeTabId
  bridgeTabId = null
  let appTab = null
  if (platformTabId) appTab = await chrome.tabs.get(platformTabId).catch(() => null)
  if (!appTab) {
    const matches = await chrome.tabs.query({ url: ['http://localhost:5173/*', 'http://127.0.0.1:5173/*'] })
    appTab = matches.at(-1) || null
    platformTabId = appTab?.id || null
  }
  if (appTab?.id) {
    await chrome.windows.update(appTab.windowId, { focused: true }).catch(() => {})
    await chrome.tabs.update(appTab.id, { active: true })
  }
  if (params.closeResearchTab !== false && researchTabId && researchTabId !== appTab?.id) {
    await chrome.tabs.remove(researchTabId).catch(() => {})
  }
  await chrome.action.setBadgeBackgroundColor({ color: '#2563EB' }).catch(() => {})
  await chrome.action.setBadgeText({ text: 'DONE' }).catch(() => {})
  setTimeout(() => chrome.action.setBadgeText({ text: '' }).catch(() => {}), 5000)
  return { platformTabId: appTab?.id || null, researchTabClosed: Boolean(researchTabId && researchTabId !== appTab?.id) }
}

function extractPageLinks(limit) {
  const preferred = [...document.querySelectorAll('li.b_algo h2 a, #search h3 a, [data-testid="result"] a, main h2 a, main h3 a')]
  const candidates = [...preferred, ...document.querySelectorAll('li.b_algo a[href], #b_results a[href], #search a[href], main a[href], article a[href]')]
  const seen = new Set(), links = []
  for (const anchor of candidates) {
    const title = (anchor.innerText || anchor.textContent || anchor.getAttribute('aria-label') || '').replace(/\s+/g, ' ').trim(), url = normalizeResultUrl(anchor.href)
    if (!title || !/^https?:/i.test(url) || seen.has(url)) continue
    if (/bing\.com\/search|google\.com\/search/i.test(url)) continue
    seen.add(url); links.push({ title: title.slice(0, 240), url })
    if (links.length >= Math.min(Math.max(limit, 1), 30)) break
  }
  return links

  function normalizeResultUrl(raw) {
    try {
      const parsed = new URL(raw)
      if (/bing\.com$/i.test(parsed.hostname) && /\/ck\/a/i.test(parsed.pathname)) {
        const encoded = parsed.searchParams.get('u') || ''
        if (encoded.startsWith('a1')) {
          const base64 = encoded.slice(2).replace(/-/g, '+').replace(/_/g, '/')
          const decoded = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='))
          if (/^https?:/i.test(decoded)) return decoded
        }
      }
      if (/google\./i.test(parsed.hostname) && parsed.pathname === '/url') return parsed.searchParams.get('q') || raw
      return raw
    } catch { return raw }
  }
}

async function bridgeAct(params, action) {
  if (!params.selector) throw new Error(`${action} 需要 selector 参数`)
  const tab = await targetTab(params)
  const outcome = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: performAction, args: [action, params.selector, params.text || ''] })
  const result = outcome[0]?.result
  if (!result?.ok) throw new Error(result?.error || `${action} 失败`)
  return { tabId: tab.id, selector: params.selector, action }
}

function performAction(action, selector, text) {
  const element = [...document.querySelectorAll(selector)].find(node => node.getClientRects().length > 0)
  if (!element) return { ok: false, error: `未找到可见元素：${selector}` }
  if (action === 'click') { element.click(); return { ok: true } }
  element.focus()
  if (element instanceof HTMLTextAreaElement || element instanceof HTMLInputElement) {
    const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), 'value')?.set
    setter ? setter.call(element, text) : element.value = text
  } else element.textContent = text
  element.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: text }))
  element.dispatchEvent(new Event('change', { bubbles: true }))
  return { ok: true }
}

async function bridgeScreenshot(params) {
  const tab = await targetTab(params)
  await chrome.tabs.update(tab.id, { active: true })
  await new Promise(resolve => setTimeout(resolve, 300))
  const imageDataUrl = await chrome.tabs.captureVisibleTab(tab.windowId, { format: 'png' })
  return { tabId: tab.id, imageDataUrl }
}
