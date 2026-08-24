package cn.finalscompass.ai.runtime.provider;

/**
 * 定义运行时供应商模型状态允许使用的固定取值
 * 维护入口：供应商、模型、端点定义及匹配规则变化时修改这里
 */
public enum RuntimeProviderModelStatus {
  ACTIVE,
  DEPRECATED,
  DISABLED
}
