package cn.finalscompass.ai.runtime.provider.client;

/**
 * 运行时HTTP传输的抽象契约，用于隔离业务编排与具体实现。
 * 维护入口：供应商 HTTP 协议、错误映射或工具调用格式变化时修改这里。
 */
public interface RuntimeHttpTransport {
  RuntimeHttpResponse postJson(RuntimeHttpRequest request);
}
