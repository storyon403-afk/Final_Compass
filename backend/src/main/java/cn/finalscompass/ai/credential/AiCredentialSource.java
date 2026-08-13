package cn.finalscompass.ai.credential;

/**
 * 定义AI凭据来源允许使用的固定取值。
 * 维护入口：凭据来源或密钥生命周期变化时修改这里。
 */
public enum AiCredentialSource {
  PLATFORM,
  STORED_BYOK,
  EPHEMERAL_BYOK
}
