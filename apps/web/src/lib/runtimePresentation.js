export function readableRuntimeResult(value) {
  if (!value) return "任务已完成。"
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value
    if (typeof parsed === "string") return parsed
    if (parsed.summary) return parsed.summary
    if (parsed.content) return parsed.content
    if (parsed.output) return typeof parsed.output === "string" ? parsed.output : JSON.stringify(parsed.output, null, 2)
    if (parsed.participants) {
      return parsed.participants
        .map((item) => `### ${item.provider_key || item.providerKey}\n\n${item.result_text || item.result || "未返回内容"}`)
        .join("\n\n")
    }
    return JSON.stringify(parsed, null, 2)
  } catch {
    return String(value)
  }
}

export function agentFailureMessage(code) {
  if (code === "HERMES_TIMEOUT") return "Hermes 生成和检查文件超过了 30 分钟，本次任务已停止。可以缩小页数后重试，或在本地提高 HERMES_RUN_TIMEOUT_MS。"
  return `Agent 任务失败：${code || "未知错误"}`
}

export function hasExplicitDivision(goal) {
  return /分工|分别|各自|并行|分头|负责|交给.{0,12}(Kimi|DeepSeek|千问|Qwen)/i.test(goal)
}

export function requestsKnowledge(goal) {
  return /(查询|检索|参考|结合|使用).{0,12}(Finals\s*Compass|平台|本地)?(知识库|数据库)/i.test(goal)
}
