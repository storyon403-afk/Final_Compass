package cn.finalscompass.ai.runtime.trace;

/**
 * 定义运行时类型允许使用的固定取值。
 * 维护入口：执行链路、状态和审计字段变化时修改这里。
 */
public enum RuntimeType {
  LEGACY,
  WORKFLOW,
  CHAT,
  AGENT,
  MULTI_WEB_AGENT
}
