#!/usr/bin/env node
// Session-scoped Hermes gateway for Finals Compass.
// The user's API key exists only in the accepted request and the spawned Hermes
// process environment. It is never written to disk or printed.

import http from 'node:http'
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { mkdtemp, readdir, readFile, rm, stat } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { basename, extname, join, relative } from 'node:path'

const PORT = Number(process.env.HERMES_AGENT_PORT || 8642)
const HERMES_BIN = process.env.HERMES_BIN || 'hermes'
const DEFAULT_BROWSER_URL = process.env.HERMES_BROWSER_URL || ''
const MAX_BODY = 64 * 1024
const MAX_OUTPUT = 2 * 1024 * 1024
const MAX_ARTIFACT_BYTES = 100 * 1024 * 1024
// Rich document tasks render and inspect every page/slide, so ten minutes is
// routinely too short. The browser UI has its own slightly longer watchdog.
const RUN_TIMEOUT_MS = Number(process.env.HERMES_RUN_TIMEOUT_MS || 30 * 60 * 1000)
const SEARCH_PLAN_TIMEOUT_MS = Number(process.env.HERMES_SEARCH_PLAN_TIMEOUT_MS || 2 * 60 * 1000)
const BROWSER_COMMAND_TIMEOUT_MS = 45 * 1000
const children = new Map()

const PROVIDERS = {
  openai: { cli: 'openai-api', key: 'OPENAI_API_KEY' },
  deepseek: { cli: 'deepseek', key: 'DEEPSEEK_API_KEY' },
  gemini: { cli: 'gemini', key: 'GEMINI_API_KEY' },
  openrouter: { cli: 'openrouter', key: 'OPENROUTER_API_KEY' },
  kimi: { cli: 'kimi-coding-cn', key: 'KIMI_CN_API_KEY' }
}
const SECRET_ENV_NAMES = [...new Set(Object.values(PROVIDERS).map(item => item.key))]

const server = http.createServer((req, res) => {
  if (req.method === 'DELETE' && req.url?.startsWith('/agent-runs/')) {
    const runId = decodeURIComponent(req.url.slice('/agent-runs/'.length))
    const child = children.get(runId)
    if (child) child.kill('SIGTERM')
    children.delete(runId)
    return json(res, 200, { runId, status: child ? 'CANCELLED' : 'NOT_RUNNING' })
  }
  if (req.method !== 'POST' || req.url !== '/agent-runs') return json(res, 404, { error: 'not found' })
  let raw = ''
  req.on('data', chunk => {
    raw += chunk
    if (Buffer.byteLength(raw) > MAX_BODY) req.destroy()
  })
  req.on('end', () => {
    let run
    try { run = JSON.parse(raw) } catch { return json(res, 400, { error: 'invalid json' }) }
    if (!validRun(run)) return json(res, 400, { error: 'invalid agent run' })
    json(res, 202, { runId: run.runId, status: 'ACCEPTED' })
    execute(run).catch(error => {
      console.error(`[hermes-agent] run ${safeId(run.runId)} failed: ${error.message}`)
      postStatus(run, 'FAILED', null, error.code || 'HERMES_AGENT_ERROR').catch(() => {})
    })
  })
})

function validRun(run) {
  return run && typeof run.runId === 'string' && typeof run.goal === 'string'
    && run.goal.trim() && typeof run.callbackBase === 'string'
    && typeof run.callbackToken === 'string' && run.callbackToken
    && typeof run.ephemeralApiKey === 'string' && run.ephemeralApiKey.trim()
    && PROVIDERS[String(run.provider || '').toLowerCase()]
}

async function execute(run) {
  await postStatus(run, 'RUNNING')
  const workDir = await mkdtemp(join(tmpdir(), `finals-hermes-${safeId(run.runId)}-`))
  try {
    const browserContext = await researchBrowser(run, workDir)
    const knowledgeContext = run.knowledgeContext ? `\n\n${String(run.knowledgeContext).slice(0, 8000)}` : ''
    const outputRules = `\n\n系统文件规则：如果任务要求生成 PDF、DOCX、PPTX、XLSX、图片或其他文件，必须只写入当前工作目录 ${workDir}，禁止写入桌面、下载目录或用户主目录。最终回答请列出生成的文件名。`
    const answer = await invokeHermes(run, `${knowledgeContext}${browserContext}${outputRules}`, workDir)
    const generated = await generatedFiles(workDir)
    if (generated.length) {
      for (const file of generated) await uploadFile(run, file.path, file.name, file.size)
    }
    await postStatus(run, 'COMPLETED', answer.slice(0, 4000))
    console.log(`[hermes-agent] run ${safeId(run.runId)} completed with ${generated.length} file(s); temporary workspace and credential released`)
  } finally {
    await rm(workDir, { recursive: true, force: true })
  }
}

async function researchBrowser(run, workDir) {
  if (!run.allowWebSearch) return ''
  try {
  const goal = currentGoal(run.goal), directUrls = allUrls(goal).slice(0, 3)
  let queries = []
  if (!directUrls.length) {
    try { queries = await planSearchQueries(run, goal, workDir) }
    catch (error) {
      console.warn(`[hermes-agent] run ${safeId(run.runId)} search planning fallback: ${error.message}`)
      queries = [fallbackSearchQuery(goal)]
    }
  }
  const candidates = directUrls.map(url => ({ title: '用户指定页面', url })), seen = new Set(directUrls)
  for (const query of queries.slice(0, 3)) {
    try {
      console.log(`[hermes-agent] run ${safeId(run.runId)} searching: ${safeLog(query)}`)
      const searchUrl = DEFAULT_BROWSER_URL
        ? DEFAULT_BROWSER_URL.replace('{query}', encodeURIComponent(query))
        : `https://www.bing.com/search?q=${encodeURIComponent(query)}`
      const page = await browserCommand(run, 'navigate', { url: searchUrl })
      const found = await browserCommand(run, 'get_links', { tabId: page.tabId, limit: 8 })
      let added = 0
      for (const link of found.links || []) {
        if (candidates.length >= 7 || seen.has(link.url) || !safeResearchUrl(link.url)) continue
        seen.add(link.url); candidates.push({ title: link.title || query, url: link.url })
        if (++added >= 2) break
      }
    } catch (error) { console.warn(`[hermes-agent] search step skipped: ${error.message}`) }
  }
  const evidence = []
  for (const candidate of candidates.slice(0, 6)) {
    try {
      console.log(`[hermes-agent] run ${safeId(run.runId)} reading: ${safeLog(candidate.url)}`)
      const page = await browserCommand(run, 'navigate', { url: candidate.url })
      const content = await browserCommand(run, 'get_content', { tabId: page.tabId })
      const text = String(content.text || '').replace(/\s+/g, ' ').trim().slice(0, 7000)
      if (text.length >= 120) evidence.push(`来源：${content.title || candidate.title}\nURL：${content.url || candidate.url}\n正文：${text}`)
    } catch (error) { console.warn(`[hermes-agent] page skipped: ${error.message}`) }
  }
  if (!evidence.length) return '\n\n浏览器检索未取得可读页面，请基于已有知识回答并明确说明未能取得网页证据。'
  return `\n\n浏览器自主检索资料（共 ${evidence.length} 个页面；回答时保留对应 URL）：\n\n${evidence.join('\n\n---\n\n')}`.slice(0, 32000)
  } finally {
    try {
      await browserCommand(run, 'finish_research', { closeResearchTab: true })
      console.log(`[hermes-agent] run ${safeId(run.runId)} browser research finished; returned to Finals Compass`)
    } catch (error) {
      console.warn(`[hermes-agent] unable to return to platform: ${error.message}`)
    }
  }
}

async function planSearchQueries(run, goal, workDir) {
  const prompt = `你是浏览器检索规划器。理解用户目标，生成 1 到 3 个适合搜索引擎的精确检索词。不要执行任务、不要调用工具、不要解释。只输出 JSON：{"queries":["检索词"]}\n\n用户目标：${goal.slice(0, 5000)}`
  const answer = await invokeHermes(run, '', workDir, SEARCH_PLAN_TIMEOUT_MS, prompt)
  const match = answer.match(/\{[\s\S]*?"queries"[\s\S]*?\}/)
  const parsed = match ? JSON.parse(match[0]) : null
  const queries = Array.isArray(parsed?.queries) ? parsed.queries.map(value => String(value).trim().slice(0, 180)).filter(Boolean) : []
  if (!queries.length) throw new Error('Hermes returned no search queries')
  return queries.slice(0, 3)
}

async function browserCommand(run, command, params) {
  const base = run.callbackBase.replace(/\/$/, '')
  const response = await postJson(`${base}/runs/${encodeURIComponent(run.runId)}/browser/commands`, run.callbackToken, {
    command, params, timeoutMs: BROWSER_COMMAND_TIMEOUT_MS
  })
  if (response?.status !== 'COMPLETED') throw new Error(response?.error || `${command} failed`)
  return response.result || {}
}

function invokeHermes(run, browserContext, workDir, timeoutMs = RUN_TIMEOUT_MS, goalOverride = null) {
  const selected = PROVIDERS[String(run.provider).toLowerCase()]
  const args = ['-z', goalOverride ?? `${run.goal}${browserContext}`, '--provider', selected.cli]
  if (run.model) args.push('--model', String(run.model))
  const env = { ...process.env }
  for (const name of SECRET_ENV_NAMES) delete env[name]
  env[selected.key] = run.ephemeralApiKey
  const hermesPython = join(HERMES_BIN.includes('/') ? join(HERMES_BIN, '..') : '', 'python')
  if (HERMES_BIN.includes('/') && existsSync(hermesPython)) env.HERMES_PYTHON = hermesPython

  return new Promise((resolve, reject) => {
    console.log(`[hermes-agent] run ${safeId(run.runId)} started (timeout ${Math.round(timeoutMs / 60000)} min)`)
    const child = spawn(HERMES_BIN, args, { cwd: workDir, env, stdio: ['ignore', 'pipe', 'pipe'] })
    children.set(run.runId, child)
    let stdout = '', stderr = ''
    const timer = setTimeout(() => {
      const error = new Error(`Hermes run exceeded ${Math.round(timeoutMs / 60000)} minutes`)
      error.code = 'HERMES_TIMEOUT'
      child.kill('SIGTERM')
      reject(error)
    }, timeoutMs)
    child.stdout.on('data', chunk => { if (stdout.length < MAX_OUTPUT) stdout += chunk })
    child.stderr.on('data', chunk => { if (stderr.length < 16000) stderr += chunk })
    child.on('error', error => { clearTimeout(timer); children.delete(run.runId); error.code = 'HERMES_NOT_FOUND'; reject(error) })
    child.on('close', code => {
      clearTimeout(timer)
      children.delete(run.runId)
      if (code === 0 && stdout.trim()) resolve(stdout.trim())
      else {
        const error = new Error(`Hermes exited with ${code}: ${stderr.trim().slice(-1000)}`)
        error.code = 'HERMES_EXECUTION_FAILED'
        reject(error)
      }
    })
  })
}

async function uploadFile(run, path, name, size) {
  if (size < 1 || size > MAX_ARTIFACT_BYTES) throw new Error(`Generated file size is invalid: ${name}`)
  return uploadBytes(run, name, contentType(name), await readFile(path))
}

async function uploadBytes(run, fileName, type, content) {
  const base = run.callbackBase.replace(/\/$/, '')
  await postJson(`${base}/runs/${encodeURIComponent(run.runId)}/artifacts`, run.callbackToken, {
    fileName, contentType: type, contentBase64: content.toString('base64')
  })
}

async function generatedFiles(root) {
  const found = []
  async function walk(dir) {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) await walk(path)
      else if (entry.isFile()) {
        const info = await stat(path)
        found.push({ path, name: safeFileName(relative(root, path)), size: info.size })
      }
    }
  }
  await walk(root)
  return found.sort((a, b) => a.name.localeCompare(b.name)).slice(0, 12)
}

function safeFileName(value) { return basename(value).replace(/[\\/\r\n\0"]/g, '_').slice(0, 180) || 'agent-output.bin' }
function contentType(name) {
  return ({ '.pdf': 'application/pdf', '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', '.pptx': 'application/vnd.openxmlformats-officedocument.presentationml.presentation', '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', '.md': 'text/markdown', '.txt': 'text/plain', '.html': 'text/html', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.json': 'application/json' })[extname(name).toLowerCase()] || 'application/octet-stream'
}

async function postStatus(run, status, summary = null, errorCode = null) {
  const base = run.callbackBase.replace(/\/$/, '')
  return postJson(`${base}/runs/${encodeURIComponent(run.runId)}/status`, run.callbackToken, { status, summary, errorCode })
}

async function postJson(url, token, body) {
  const response = await fetch(url, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body), signal: AbortSignal.timeout(125000)
  })
  const text = await response.text()
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${text.slice(0, 300)}`)
  try { return JSON.parse(text) } catch { return text }
}

function allUrls(value) { return [...String(value).matchAll(/https?:\/\/[^\s<>()"']+/gi)].map(match => match[0]) }
function currentGoal(value) { return String(value).split(/当前任务：/).at(-1).trim() }
function fallbackSearchQuery(value) { return value.replace(/https?:\/\/\S+/g, ' ').replace(/[#*_`>\[\](){}]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 160) }
function safeResearchUrl(value) { try { const url = new URL(value); return /^https?:$/.test(url.protocol) && !/(^|\.)(bing|google)\.com$/i.test(url.hostname) } catch { return false } }
function safeLog(value) { return String(value).replace(/[\r\n]/g, ' ').slice(0, 180) }
function safeId(value) { return String(value).replace(/[^a-zA-Z0-9-]/g, '').slice(0, 40) }
function json(res, status, body) { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(body)) }

function shutdown() {
  for (const child of children.values()) child.kill('SIGTERM')
  server.close(() => process.exit(0))
  setTimeout(() => process.exit(1), 3000).unref()
}
process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[hermes-agent] listening on http://127.0.0.1:${PORT}; credentials are process-scoped`)
})
