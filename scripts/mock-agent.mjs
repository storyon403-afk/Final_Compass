#!/usr/bin/env node
// Mock local Agent Gateway for Finals Compass end-to-end testing.
// Implements POST /agent-runs, calls back the backend, drives the browser bridge,
// uploads one artifact, and completes the run.
//
// Usage: node scripts/mock-agent.mjs
// Env:   MOCK_AGENT_PORT (default 8642)
//        MOCK_BROWSER_URL (default https://example.com)

import http from 'node:http'

const PORT = Number(process.env.MOCK_AGENT_PORT || 8642)
const BROWSER_URL = process.env.MOCK_BROWSER_URL || 'https://example.com'

const server = http.createServer((req, res) => {
  if (req.method === 'POST' && req.url === '/agent-runs') {
    let raw = ''
    req.on('data', chunk => { raw += chunk })
    req.on('end', () => {
      let run
      try { run = JSON.parse(raw) } catch {
        res.writeHead(400, { 'Content-Type': 'application/json' })
        return res.end(JSON.stringify({ error: 'invalid json' }))
      }
      console.log(`[mock-agent] received run ${run.runId}: ${String(run.goal || '').slice(0, 80)}`)
      res.writeHead(202, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ runId: run.runId, status: 'ACCEPTED' }))
      executeRun(run).catch(error => {
        console.error('[mock-agent] run failed:', error.message)
        postStatus(run, 'FAILED', null, 'MOCK_AGENT_ERROR').catch(() => {})
      })
    })
    return
  }
  res.writeHead(404, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify({ error: 'not found' }))
})

async function executeRun(run) {
  const { runId, callbackBase, callbackToken } = run
  if (!callbackBase || !callbackToken) throw new Error('callbackBase/callbackToken missing')
  const base = callbackBase.replace(/\/$/, '')

  await postStatus(run, 'RUNNING')

  const nav = await postJson(`${base}/runs/${runId}/browser/commands`, callbackToken, {
    command: 'navigate', params: { url: BROWSER_URL }, timeoutMs: 60000
  })
  console.log('[mock-agent] navigated:', JSON.stringify(nav.result || nav).slice(0, 200))

  const content = await postJson(`${base}/runs/${runId}/browser/commands`, callbackToken, {
    command: 'get_content', params: {}, timeoutMs: 60000
  })
  const page = content.result || {}
  console.log(`[mock-agent] got content: ${String(page.title || '')} (${String(page.text || '').length} chars)`)

  const report = [
    '# Mock Agent 报告',
    '',
    `- 任务目标：${run.goal || ''}`,
    `- 抓取页面：${page.url || BROWSER_URL}`,
    `- 页面标题：${page.title || ''}`,
    '',
    '## 页面正文（节选）',
    '',
    String(page.text || '（空）').slice(0, 2000),
    ''
  ].join('\n')

  const artifact = await postJson(`${base}/runs/${runId}/artifacts`, callbackToken, {
    fileName: 'agent-report.md',
    contentType: 'text/markdown',
    contentBase64: Buffer.from(report, 'utf8').toString('base64')
  })
  console.log('[mock-agent] artifact uploaded:', JSON.stringify(artifact))

  await postStatus(run, 'COMPLETED', `已通过浏览器读取 ${page.url || BROWSER_URL} 并生成报告 agent-report.md`)
  console.log(`[mock-agent] run ${runId} completed`)
}

async function postStatus(run, status, summary = null, errorCode = null) {
  const base = run.callbackBase.replace(/\/$/, '')
  await postJson(`${base}/runs/${run.runId}/status`, run.callbackToken, { status, summary, errorCode })
}

async function postJson(url, token, body) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body)
  })
  const text = await response.text()
  if (response.status >= 300) throw new Error(`HTTP ${response.status} from ${url}: ${text.slice(0, 300)}`)
  try { return JSON.parse(text) } catch { return text }
}

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[mock-agent] listening on http://127.0.0.1:${PORT} (browser target: ${BROWSER_URL})`)
})
