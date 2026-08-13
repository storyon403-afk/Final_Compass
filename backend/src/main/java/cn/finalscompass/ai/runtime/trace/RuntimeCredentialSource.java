package cn.finalscompass.ai.runtime.trace;

/**
 * 定义运行时凭据来源允许使用的固定取值。
 * 维护入口：执行链路、状态和审计字段变化时修改这里。
 */
public enum RuntimeCredentialSource {
  PLATFORM,
  STORED_BYOK,
  EPHEMERAL_BYOK
}
