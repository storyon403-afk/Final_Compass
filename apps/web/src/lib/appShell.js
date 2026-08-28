export function routeLocationLabel(path, params = {}) {
  if (path === '/') return '课程导航'
  if (path.startsWith('/question-vine')) return '问题藤'
  if (params.teacherId) return '老师圈'
  if (params.courseId) return '任课老师'
  return '课程导航'
}

export const moderationLabel = (type) => ({ RESOURCE: '复习资料', DISCUSSION: '匿名讨论', GUIDE_SUBMISSION: '指南参考' })[type] || type
export const betaStatusLabel = (status) => ({ CREATED: '正在发送', CODE_SENT: '等待用户验证', EMAIL_VERIFIED: '邮箱已验证', ACCOUNT_CREATED: '账号已创建', CREDENTIAL_SENT: '账号已发送', EXPIRED: '已失效' })[status] || status

export function emailHtml(text) {
  const escape = (value) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const lines = text.split('\n').map((line, index) => {
    let content = escape(line)
    if (index === 0 || /^(账号|初始密码)：/.test(line)) content = `<strong>${content}</strong>`
    else content = content.replace(/(\d{6})/g, '<strong style="font-size:20px;letter-spacing:3px">$1</strong>')
    return line ? `<div>${content}</div>` : '<div><br></div>'
  }).join('')
  return `<div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;font-size:14px;line-height:1.75;color:#222">${lines}</div>`
}
