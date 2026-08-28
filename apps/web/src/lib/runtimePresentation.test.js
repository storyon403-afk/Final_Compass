import assert from 'node:assert/strict'
import test from 'node:test'
import { agentFailureMessage, hasExplicitDivision, readableRuntimeResult, requestsKnowledge } from './runtimePresentation.js'

test('normalizes structured runtime results', () => {
  assert.equal(readableRuntimeResult('{"summary":"完成"}'), '完成')
  assert.match(readableRuntimeResult({ participants: [{ providerKey: 'KIMI', result: '答案' }] }), /KIMI[\s\S]*答案/)
})

test('recognizes routing intent', () => {
  assert.equal(hasExplicitDivision('请让 Kimi 和 Qwen 分工完成'), true)
  assert.equal(requestsKnowledge('请结合平台知识库回答'), true)
  assert.equal(requestsKnowledge('直接回答常识问题'), false)
})

test('produces actionable agent failure text', () => {
  assert.match(agentFailureMessage('HERMES_TIMEOUT'), /超过了 30 分钟/)
  assert.match(agentFailureMessage('BROKEN'), /BROKEN/)
})
