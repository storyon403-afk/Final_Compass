package cn.finalscompass.ai.runtime.tool;

/**
 * 定义运行时工具传输类型允许使用的固定取值
 * 维护入口：运行时工具定义、权限和执行契约变化时修改这里
 */
public enum RuntimeToolTransportType {
  INTERNAL,
  MCP,
  HTTP,
  BROWSER
}
