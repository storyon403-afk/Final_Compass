package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.provider.client.RuntimeBinaryInput;
import cn.finalscompass.ai.runtime.provider.client.RuntimeProviderClientException;
import cn.finalscompass.ai.runtime.provider.client.RuntimeProviderClientRegistry;
import cn.finalscompass.ai.runtime.provider.client.RuntimeProviderClientResult;
import cn.finalscompass.ai.runtime.provider.client.RuntimeProviderContinuation;
import cn.finalscompass.ai.runtime.tool.RuntimeToolCallResult;
import cn.finalscompass.ai.runtime.tool.RuntimeToolExecutionContext;
import cn.finalscompass.ai.runtime.tool.RuntimeToolExecutor;
import cn.finalscompass.ai.runtime.trace.CreateRuntimeProviderInvocation;
import cn.finalscompass.ai.runtime.trace.RuntimeCredentialSource;
import cn.finalscompass.ai.runtime.trace.RuntimeExecutionTraceStore;
import cn.finalscompass.ai.runtime.trace.RuntimeProviderInvocationResult;
import cn.finalscompass.ai.runtime.trace.RuntimeProviderInvocationStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/*
 * 维护流程图：
 *   Dispatch 主命令 --> ProviderClient.invoke --> 有工具调用？
 *        |失败                                  |是
 *        v                                      v
 *   fallback 候选 <-- 记录 Invocation <-- ToolExecutor --> continue
 *        \------------------> ExecutionResult + Trace
 */
/**
 * 统一编排模型调用、供应商回退、工具多轮执行、用量成本和追踪记录
 * 维护入口：调用主流程与回退改这里；供应商报文改 client；工具安全契约改 RuntimeToolExecutor
 */
@Component
public final class LegacyRuntimeModelClientGateway implements RuntimeModelClientGateway {
  private final RuntimeExecutionTraceStore traces;
  private final RuntimeProviderClientRegistry nativeClients;
  private final RuntimeToolExecutor toolExecutor;

  @Autowired
  public LegacyRuntimeModelClientGateway(
      RuntimeExecutionTraceStore traces,
      RuntimeProviderClientRegistry nativeClients,
      RuntimeToolExecutor toolExecutor) {
    this.traces = traces;
    this.nativeClients = nativeClients;
    this.toolExecutor = toolExecutor;
  }

  LegacyRuntimeModelClientGateway(
      RuntimeExecutionTraceStore traces, RuntimeProviderClientRegistry nativeClients) {
    this(traces, nativeClients, null);
  }

  /**
   * 执行一次运行时调用
   *
   * @param executionNodeId executionNode 对应的数据库 ID
   * @param dispatch 包含主命令和备用命令的调度计划
   * @param credentials 按候选模型动态解析凭据的回调
   * @param binaryInput 可选的图片等二进制输入
   * @return 处理后的业务结果
   */
  @Override
  public RuntimeModelExecutionResult execute(
      long executionNodeId,
      RuntimeModelDispatch dispatch,
      RuntimeCredentialLeaseResolver credentials,
      RuntimeBinaryInput binaryInput) {
    return executeInternal(executionNodeId, dispatch, credentials, binaryInput, null, 0, 0);
  }

  /**
   * 执行一次运行时调用
   *
   * @param executionNodeId executionNode 对应的数据库 ID
   * @param dispatch 包含主命令和备用命令的调度计划
   * @param credentials 按候选模型动态解析凭据的回调
   * @param binaryInput 可选的图片等二进制输入
   * @param attemptOffset 当前候选项在完整尝试链中的偏移量
   * @return 处理后的业务结果
   */
  @Override
  public RuntimeModelExecutionResult execute(
      long executionNodeId,
      RuntimeModelDispatch dispatch,
      RuntimeCredentialLeaseResolver credentials,
      RuntimeBinaryInput binaryInput,
      int attemptOffset) {
    if (attemptOffset < 0)
      throw new IllegalArgumentException("Runtime model attempt offset is invalid");
    return executeInternal(
        executionNodeId, dispatch, credentials, binaryInput, null, 0, attemptOffset);
  }

  /**
   * 执行一次运行时调用
   *
   * @param dispatch 包含主命令和备用命令的调度计划
   * @param credentials 按候选模型动态解析凭据的回调
   * @param binaryInput 可选的图片等二进制输入
   * @param toolContext 工具执行时使用的用户与权限上下文
   * @param maxToolRounds 允许模型与工具往返的最大轮数
   * @return 处理后的业务结果
   */
  @Override
  public RuntimeModelExecutionResult executeWithTools(
      RuntimeModelDispatch dispatch,
      RuntimeCredentialLeaseResolver credentials,
      RuntimeBinaryInput binaryInput,
      RuntimeToolExecutionContext toolContext,
      int maxToolRounds) {
    if (toolContext == null
        || toolContext.executionId() <= 0
        || toolContext.nodeId() <= 0
        || toolContext.userId() <= 0
        || maxToolRounds < 1
        || maxToolRounds > 8
        || toolExecutor == null)
      throw new IllegalArgumentException("Runtime Tool conversation input is invalid");
    return executeInternal(
        toolContext.nodeId(), dispatch, credentials, binaryInput, toolContext, maxToolRounds, 0);
  }

  /**
   * 执行一次运行时调用
   * 实现上，主路径不可用时按候选顺序尝试备用项，提高调用成功率；状态变化先经过状态机约束，阻止非法跳转
   * 可升级：该方法职责较多，后续可按校验、执行和结果持久化拆分
   *
   * @param executionNodeId executionNode 对应的数据库 ID
   * @param dispatch 包含主命令和备用命令的调度计划
   * @param credentials 按候选模型动态解析凭据的回调
   * @param binaryInput 可选的图片等二进制输入
   * @param toolContext 工具执行时使用的用户与权限上下文
   * @param maxToolRounds 允许模型与工具往返的最大轮数
   * @param attemptOffset 当前候选项在完整尝试链中的偏移量
   * @return 处理后的业务结果
   */
  private RuntimeModelExecutionResult executeInternal(
      long executionNodeId,
      RuntimeModelDispatch dispatch,
      RuntimeCredentialLeaseResolver credentials,
      RuntimeBinaryInput binaryInput,
      RuntimeToolExecutionContext toolContext,
      int maxToolRounds,
      int attemptOffset) {
    if (executionNodeId <= 0 || dispatch == null || credentials == null)
      throw new IllegalArgumentException("Runtime model execution input is invalid");
    List<RuntimeModelInvocationCommand> candidates = new ArrayList<>();
    candidates.add(dispatch.primary());
    candidates.addAll(dispatch.fallbacks());
    Long fallbackFrom = null;
    RuntimeException lastFailure = null;
    for (int index = 0; index < candidates.size(); index++) {
      RuntimeModelInvocationCommand command = candidates.get(index);
      long invocationId =
          traces.createProviderInvocation(
              new CreateRuntimeProviderInvocation(
                  UUID.randomUUID().toString(),
                  executionNodeId,
                  command.providerId(),
                  command.providerModelId(),
                  command.providerKey(),
                  command.modelKey(),
                  RuntimeCredentialSource.valueOf(command.credentialSource()),
                  attemptOffset + index + 1,
                  fallbackFrom,
                  "{}"));
      long started = System.nanoTime();
      try (ResolvedAiCredential credential = credentials.resolve(command)) {
        requireMatchingCredential(command, credential);
        traces.transitionProviderInvocation(
            invocationId, RuntimeProviderInvocationStatus.RUNNING, null);
        ClientResult result =
            invokeCandidate(command, credential, binaryInput, toolContext, maxToolRounds);
        long latencyMs = elapsedMillis(started);
        traces.transitionProviderInvocation(
            invocationId,
            RuntimeProviderInvocationStatus.SUCCEEDED,
            invocationResult(command, result, latencyMs));
        return new RuntimeModelExecutionResult(
            result.content(),
            result.inputUnits(),
            result.outputUnits(),
            result.preview(),
            command.providerKey(),
            command.modelKey(),
            invocationId,
            result.toolCalls());
      } catch (RuntimeException exception) {
        RuntimeProviderInvocationStatus status =
            timeout(exception)
                ? RuntimeProviderInvocationStatus.TIMEOUT
                : RuntimeProviderInvocationStatus.FAILED;
        String errorCode =
            status == RuntimeProviderInvocationStatus.TIMEOUT
                ? "PROVIDER_TIMEOUT"
                : "PROVIDER_ERROR";
        traces.transitionProviderInvocation(
            invocationId,
            status,
            new RuntimeProviderInvocationResult(
                0,
                0,
                null,
                null,
                elapsedMillis(started),
                null,
                errorCode,
                exception.getClass().getSimpleName(),
                "{}"));
        fallbackFrom = invocationId;
        lastFailure = exception;
        if (exception instanceof ToolConversationException)
          throw new RuntimeModelExecutionException("Runtime Tool conversation failed", exception);
      }
    }
    String detail =
        lastFailure == null
                || lastFailure.getMessage() == null
                || lastFailure.getMessage().isBlank()
            ? ""
            : "：" + lastFailure.getMessage();
    throw new RuntimeModelExecutionException(
        "All Runtime Provider candidates failed" + detail, lastFailure);
  }

  /**
   * 调用外部服务并解析返回结果
   * 实现上，局部失败会降级为空结果，不让辅助能力中断主流程
   * 可升级：该方法职责较多，后续可按校验、执行和结果持久化拆分
   *
   * @param command 已经归一化的执行命令
   * @param credential 本次调用使用的凭据
   * @param binaryInput 可选的图片等二进制输入
   * @param toolContext 工具执行时使用的用户与权限上下文
   * @param maxToolRounds 允许模型与工具往返的最大轮数
   * @return 处理后的业务结果
   */
  private ClientResult invokeCandidate(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeBinaryInput binaryInput,
      RuntimeToolExecutionContext toolContext,
      int maxToolRounds) {
    var nativeClient =
        nativeClients
            .find(command.adapterKey())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Runtime Provider adapter is not registered: " + command.adapterKey()));
    RuntimeProviderClientResult result = nativeClient.invoke(command, credential, binaryInput);
    if (result.toolCalls().isEmpty() || toolContext == null) return clientResult(result);
    try {
      if (!command.skillKey().equals(toolContext.skillKey())
          || !toolContext.allowedTools().equals(command.allowedTools()))
        throw new SecurityException("Runtime Tool context does not match Skill dispatch");
      int inputUnits = result.inputUnits();
      int outputUnits = result.outputUnits();
      for (int round = 1; ; round++) {
        if (round > maxToolRounds)
          throw new IllegalStateException("Runtime Tool conversation exceeded maximum rounds");
        List<RuntimeToolCallResult> outputs = new ArrayList<>();
        for (var call : result.toolCalls()) {
          traces.appendEvent(
              toolContext.executionId(),
              toolContext.nodeId(),
              "TOOL_CALL_REQUESTED",
              "{\"round\":"
                  + round
                  + ",\"callId\":\""
                  + call.callId()
                  + "\",\"toolKey\":\""
                  + call.toolKey()
                  + "\"}");
          RuntimeToolCallResult output = toolExecutor.execute(toolContext, call);
          outputs.add(output);
          traces.appendEvent(
              toolContext.executionId(),
              toolContext.nodeId(),
              "TOOL_CALL_COMPLETED",
              "{\"round\":"
                  + round
                  + ",\"callId\":\""
                  + call.callId()
                  + "\",\"toolKey\":\""
                  + call.toolKey()
                  + "\",\"success\":true}");
        }
        if (result.continuationState() == null)
          throw new IllegalStateException("Runtime Provider omitted Tool continuation state");
        result =
            nativeClient.continueWithToolResults(
                command,
                credential,
                new RuntimeProviderContinuation(result.continuationState(), outputs));
        inputUnits += result.inputUnits();
        outputUnits += result.outputUnits();
        traces.appendEvent(
            toolContext.executionId(),
            toolContext.nodeId(),
            "TOOL_MODEL_CONTINUED",
            "{\"round\":" + round + ",\"additionalCalls\":" + result.toolCalls().size() + "}");
        if (result.toolCalls().isEmpty())
          return new ClientResult(
              result.content(),
              inputUnits,
              outputUnits,
              result.preview(),
              result.providerRequestId(),
              List.of());
      }
    } catch (RuntimeException exception) {
      throw new ToolConversationException(exception);
    }
  }

  // 把供应商响应转换为统一客户端结果
  private ClientResult clientResult(RuntimeProviderClientResult result) {
    return new ClientResult(
        result.content(),
        result.inputUnits(),
        result.outputUnits(),
        result.preview(),
        result.providerRequestId(),
        result.toolCalls());
  }

  // 把客户端结果转换为追踪记录
  private RuntimeProviderInvocationResult invocationResult(
      RuntimeModelInvocationCommand command, ClientResult result, long latencyMs) {
    BigDecimal cost = cost(command, result.inputUnits(), result.outputUnits());
    return new RuntimeProviderInvocationResult(
        result.inputUnits(),
        result.outputUnits(),
        cost,
        cost == null ? null : command.pricingCurrency(),
        latencyMs,
        result.providerRequestId(),
        null,
        null,
        "{}");
  }

  // 根据输入输出用量计算本次调用成本
  private BigDecimal cost(
      RuntimeModelInvocationCommand command, long inputUnits, long outputUnits) {
    if (command.inputUnitPrice() == null && command.outputUnitPrice() == null) return null;
    BigDecimal input =
        command.inputUnitPrice() == null ? BigDecimal.ZERO : command.inputUnitPrice();
    BigDecimal output =
        command.outputUnitPrice() == null ? BigDecimal.ZERO : command.outputUnitPrice();
    return input
        .multiply(BigDecimal.valueOf(inputUnits))
        .add(output.multiply(BigDecimal.valueOf(outputUnits)));
  }

  // 校验凭据与候选供应商是否匹配
  private void requireMatchingCredential(
      RuntimeModelInvocationCommand command, ResolvedAiCredential credential) {
    if (credential == null
        || !command.providerKey().equals(credential.provider())
        || !command.modelKey().equals(credential.model())
        || !command.credentialSource().equals(credential.source().name()))
      throw new SecurityException(
          "Resolved credential does not match the selected Provider candidate");
  }

  // 判断异常链中是否包含超时异常
  private boolean timeout(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause())
      if (current.getClass().getSimpleName().toLowerCase().contains("timeout")
          || current instanceof RuntimeProviderClientException providerFailure
              && providerFailure.errorCode().contains("TIMEOUT")) return true;
    return false;
  }

  private long elapsedMillis(long started) {
    return Math.max(0, (System.nanoTime() - started) / 1_000_000);
  }

  private record ClientResult(
      String content,
      int inputUnits,
      int outputUnits,
      boolean preview,
      String providerRequestId,
      List<cn.finalscompass.ai.runtime.tool.RuntimeToolCall> toolCalls) {}

  private static final class ToolConversationException extends RuntimeException {
    private ToolConversationException(Throwable cause) {
      super(cause);
    }
  }
}
