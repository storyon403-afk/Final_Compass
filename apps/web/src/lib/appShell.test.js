import assert from 'node:assert/strict'
import test from 'node:test'
import { betaStatusLabel, emailHtml, moderationLabel, routeLocationLabel } from './appShell.js'

test('maps routes and administrative statuses', () => {
  assert.equal(routeLocationLabel('/question-vine'), '问题藤')
  assert.equal(routeLocationLabel('/courses/math/t/lee', { teacherId: 'lee' }), '老师圈')
  assert.equal(moderationLabel('RESOURCE'), '复习资料')
  assert.equal(betaStatusLabel('EMAIL_VERIFIED'), '邮箱已验证')
})

test('email HTML escapes untrusted content', () => {
  const html = emailHtml('欢迎 <user>\n验证码：123456')
  assert.doesNotMatch(html, /<user>/)
  assert.match(html, /&lt;user&gt;/)
})
