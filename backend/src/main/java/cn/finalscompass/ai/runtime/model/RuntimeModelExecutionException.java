package cn.finalscompass.ai.runtime.model;

/**
 * 表示运行时模型执行Exception场景下可识别并向上层传播的失败。
 * 维护入口：统一模型命令、回退和执行结果契约变化时修改这里。
 */
public final class RuntimeModelExecutionException extends RuntimeException {
  public RuntimeModelExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
