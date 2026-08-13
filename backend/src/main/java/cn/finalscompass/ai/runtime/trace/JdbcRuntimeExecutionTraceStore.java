package cn.finalscompass.ai.runtime.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/*
 * 维护流程图：
 *   Execution
 *      +-- Node
 *      |    +-- ProviderInvocation
 *      +-- Event（每次状态变化的审计记录）
 *   所有状态更新 --> RuntimeTraceStateMachine 校验 --> 事务写入
 */
/**
 * 把执行、节点、供应商调用和事件日志写入数据库，并通过状态机保证流转合法。
 * 维护入口：追踪字段和事务写入改这里；允许的状态转换改 RuntimeTraceStateMachine。
 */
@Repository
public class JdbcRuntimeExecutionTraceStore implements RuntimeExecutionTraceStore {
  private static final Pattern EXTERNAL_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,79}$");
  private static final Pattern NODE_KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,99}$");
  private static final Pattern PROVIDER_KEY = Pattern.compile("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$");
  private static final Pattern MODEL_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:/-]{1,119}$");
  private static final Pattern EVENT_TYPE = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");
  private static final Pattern ERROR_CODE = Pattern.compile("^[A-Z][A-Z0-9_.:-]{1,79}$");
  private static final Pattern DIGEST = Pattern.compile("^[0-9a-f]{64}$");
  private static final java.util.Set<String> FORBIDDEN_JSON_FIELDS =
      java.util.Set.of(
          "rawinput",
          "rawoutput",
          "inputtext",
          "outputtext",
          "prompt",
          "response",
          "apikey",
          "credential",
          "secret");
  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;
  private final ObjectMapper json;
  private final RuntimeTraceStateMachine states;

  public JdbcRuntimeExecutionTraceStore(
      JdbcClient jdbc,
      TransactionTemplate transactions,
      ObjectMapper json,
      RuntimeTraceStateMachine states) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.json = json;
    this.states = states;
  }

  // 创建一次运行时执行记录。在事务边界内完成相关写操作，避免只更新部分数据；使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
  @Override
  public long createExecution(CreateRuntimeExecution command) {
    validate(command);
    Long id =
        transactions.execute(
            status -> {
              jdbc.sql(
                      """
INSERT INTO ai_runtime_execution(
  execution_id,trace_id,parent_execution_id,legacy_task_id,user_id,session_id,
  runtime_type,goal_summary,input_reference,workflow_key,workflow_version,metadata)
VALUES (:externalId,:traceId,:parentId,:legacyTaskId,:userId,:sessionId,
  :runtimeType,:goalSummary,:inputReference,:workflowKey,:workflowVersion,:metadata)
""")
                  .param("externalId", command.executionId())
                  .param("traceId", command.traceId())
                  .param("parentId", command.parentExecutionId())
                  .param("legacyTaskId", command.legacyTaskId())
                  .param("userId", command.userId())
                  .param("sessionId", command.sessionId())
                  .param("runtimeType", command.runtimeType().name())
                  .param("goalSummary", command.goalSummary())
                  .param("inputReference", command.inputReference())
                  .param("workflowKey", command.workflowKey())
                  .param("workflowVersion", command.workflowVersion())
                  .param("metadata", command.metadataJson())
                  .update();
              long executionId =
                  jdbc.sql("SELECT id FROM ai_runtime_execution WHERE execution_id=:externalId")
                      .param("externalId", command.executionId())
                      .query(Long.class)
                      .single();
              appendInTransaction(
                  executionId,
                  null,
                  "EXECUTION_CREATED",
                  objectPayload(
                      "status",
                      RuntimeExecutionStatus.CREATED.name(),
                      "runtimeType",
                      command.runtimeType().name()));
              return executionId;
            });
    if (id == null) throw new IllegalStateException("Execution transaction returned no id");
    return id;
  }

  // 创建执行链路节点。在事务边界内完成相关写操作，避免只更新部分数据；使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
  @Override
  public long createNode(CreateRuntimeExecutionNode command) {
    validate(command);
    Long id =
        transactions.execute(
            status -> {
              jdbc.sql(
                      """
INSERT INTO ai_runtime_execution_node(
  execution_id,parent_node_id,node_key,node_type,skill_id,skill_version_id,
  skill_key_snapshot,skill_version_snapshot,attempt,input_reference,input_digest,metadata)
VALUES (:executionId,:parentNodeId,:nodeKey,:nodeType,:skillId,:skillVersionId,
  :skillKey,:skillVersion,:attempt,:inputReference,:inputDigest,:metadata)
""")
                  .param("executionId", command.executionId())
                  .param("parentNodeId", command.parentNodeId())
                  .param("nodeKey", command.nodeKey())
                  .param("nodeType", command.nodeType().name())
                  .param("skillId", command.skillId())
                  .param("skillVersionId", command.skillVersionId())
                  .param("skillKey", command.skillKeySnapshot())
                  .param("skillVersion", command.skillVersionSnapshot())
                  .param("attempt", command.attempt())
                  .param("inputReference", command.inputReference())
                  .param("inputDigest", command.inputDigest())
                  .param("metadata", command.metadataJson())
                  .update();
              long nodeId =
                  jdbc.sql(
                          """
SELECT id FROM ai_runtime_execution_node
WHERE execution_id=:executionId AND node_key=:nodeKey AND attempt=:attempt
""")
                      .param("executionId", command.executionId())
                      .param("nodeKey", command.nodeKey())
                      .param("attempt", command.attempt())
                      .query(Long.class)
                      .single();
              appendInTransaction(
                  command.executionId(),
                  nodeId,
                  "NODE_CREATED",
                  objectPayload(
                      "nodeKey", command.nodeKey(), "nodeType", command.nodeType().name()));
              return nodeId;
            });
    if (id == null) throw new IllegalStateException("Node transaction returned no id");
    return id;
  }

  // 创建业务对象。在事务边界内完成相关写操作，避免只更新部分数据；使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
  // 可升级：该方法职责较多，后续可按校验、执行和结果持久化拆分。
  @Override
  public long createProviderInvocation(CreateRuntimeProviderInvocation command) {
    validate(command);
    Long id =
        transactions.execute(
            status -> {
              long executionId =
                  jdbc.sql(
                          """
SELECT execution_id FROM ai_runtime_execution_node WHERE id=:nodeId FOR UPDATE
""")
                      .param("nodeId", command.executionNodeId())
                      .query(Long.class)
                      .optional()
                      .orElseThrow(
                          () -> new IllegalArgumentException("Execution node does not exist"));
              jdbc.sql(
                      """
                      INSERT INTO ai_runtime_provider_invocation(
                        invocation_id,execution_node_id,provider_id,provider_model_id,
                        provider_key_snapshot,model_key_snapshot,credential_source,attempt,
                        fallback_from_id,metadata)
                      VALUES (:invocationId,:nodeId,:providerId,:modelId,:providerKey,:modelKey,
                        :credentialSource,:attempt,:fallbackFromId,:metadata)
                      """)
                  .param("invocationId", command.invocationId())
                  .param("nodeId", command.executionNodeId())
                  .param("providerId", command.providerId())
                  .param("modelId", command.providerModelId())
                  .param("providerKey", command.providerKeySnapshot())
                  .param("modelKey", command.modelKeySnapshot())
                  .param("credentialSource", command.credentialSource().name())
                  .param("attempt", command.attempt())
                  .param("fallbackFromId", command.fallbackFromId())
                  .param("metadata", command.metadataJson())
                  .update();
              long invocationId =
                  jdbc.sql(
                          """
SELECT id FROM ai_runtime_provider_invocation WHERE invocation_id=:invocationId
""")
                      .param("invocationId", command.invocationId())
                      .query(Long.class)
                      .single();
              appendInTransaction(
                  executionId,
                  command.executionNodeId(),
                  "PROVIDER_INVOCATION_CREATED",
                  objectPayload(
                      "provider",
                      command.providerKeySnapshot(),
                      "model",
                      command.modelKeySnapshot()));
              return invocationId;
            });
    if (id == null)
      throw new IllegalStateException("Provider invocation transaction returned no id");
    return id;
  }

  /**
   * 更新执行记录状态。
   * 实现上，在事务边界内完成相关写操作，避免只更新部分数据；使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
   *
   * @param executionId 执行记录 ID
   * @param target 准备流转到的目标状态
   * @param resultReference 执行结果的外部引用
   * @param errorCode 失败时记录的业务错误码
   * @param errorSummary 便于追踪的失败摘要
   */
  @Override
  public void transitionExecution(
      long executionId,
      RuntimeExecutionStatus target,
      String resultReference,
      String errorCode,
      String errorSummary) {
    validateTransitionTarget(target, resultReference, null, errorCode, errorSummary);
    transactions.executeWithoutResult(
        status -> {
          RuntimeExecutionStatus current =
              jdbc.sql("SELECT status FROM ai_runtime_execution WHERE id=:id FOR UPDATE")
                  .param("id", executionId)
                  .query(String.class)
                  .optional()
                  .map(RuntimeExecutionStatus::valueOf)
                  .orElseThrow(() -> new IllegalArgumentException("Execution does not exist"));
          states.requireTransition(current, target);
          jdbc.sql(
                  """
UPDATE ai_runtime_execution SET status=:target,
  started_at=CASE WHEN :startExecution THEN COALESCE(started_at,CURRENT_TIMESTAMP(6)) ELSE started_at END,
  completed_at=CASE WHEN :terminal THEN CURRENT_TIMESTAMP(6) ELSE NULL END,
  result_reference=:resultReference,error_code=:errorCode,error_summary=:errorSummary
WHERE id=:id
""")
              .param("target", target.name())
              .param("startExecution", target != RuntimeExecutionStatus.CREATED)
              .param("terminal", target.terminal())
              .param("resultReference", resultReference)
              .param("errorCode", errorCode)
              .param("errorSummary", errorSummary)
              .param("id", executionId)
              .update();
          appendInTransaction(
              executionId,
              null,
              "EXECUTION_STATUS_CHANGED",
              objectPayload("from", current.name(), "to", target.name()));
        });
  }

  /**
   * 更新执行节点状态。
   * 实现上，在事务边界内完成相关写操作，避免只更新部分数据；使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
   *
   * @param nodeId 执行节点 ID
   * @param target 准备流转到的目标状态
   * @param outputReference 节点输出的外部引用
   * @param outputDigest 节点输出内容摘要
   * @param errorCode 失败时记录的业务错误码
   * @param errorSummary 便于追踪的失败摘要
   */
  @Override
  public void transitionNode(
      long nodeId,
      RuntimeExecutionNodeStatus target,
      String outputReference,
      String outputDigest,
      String errorCode,
      String errorSummary) {
    validateTransitionTarget(target, outputReference, outputDigest, errorCode, errorSummary);
    transactions.executeWithoutResult(
        status -> {
          NodeState row =
              jdbc.sql(
                      """
SELECT execution_id,status FROM ai_runtime_execution_node WHERE id=:id FOR UPDATE
""")
                  .param("id", nodeId)
                  .query(NodeState.class)
                  .optional()
                  .orElseThrow(() -> new IllegalArgumentException("Execution node does not exist"));
          RuntimeExecutionNodeStatus current = RuntimeExecutionNodeStatus.valueOf(row.status());
          states.requireTransition(current, target);
          jdbc.sql(
                  """
UPDATE ai_runtime_execution_node SET status=:target,
  started_at=CASE WHEN :startNode THEN COALESCE(started_at,CURRENT_TIMESTAMP(6)) ELSE started_at END,
  completed_at=CASE WHEN :terminal THEN CURRENT_TIMESTAMP(6) ELSE NULL END,
  output_reference=:outputReference,output_digest=:outputDigest,
  error_code=:errorCode,error_summary=:errorSummary
WHERE id=:id
""")
              .param("target", target.name())
              .param(
                  "startNode",
                  target != RuntimeExecutionNodeStatus.PENDING
                      && target != RuntimeExecutionNodeStatus.READY)
              .param("terminal", target.terminal())
              .param("outputReference", outputReference)
              .param("outputDigest", outputDigest)
              .param("errorCode", errorCode)
              .param("errorSummary", errorSummary)
              .param("id", nodeId)
              .update();
          appendInTransaction(
              row.executionId(),
              nodeId,
              "NODE_STATUS_CHANGED",
              objectPayload("from", current.name(), "to", target.name()));
        });
  }

  /**
   * 更新供应商调用状态。
   * 实现上，在事务边界内完成相关写操作，避免只更新部分数据；使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
   * 可升级：该方法职责较多，后续可按校验、执行和结果持久化拆分。
   *
   * @param invocationId invocation 对应的数据库 ID
   * @param target 准备流转到的目标状态
   * @param result 远端返回的执行结果
   */
  @Override
  public void transitionProviderInvocation(
      long invocationId,
      RuntimeProviderInvocationStatus target,
      RuntimeProviderInvocationResult result) {
    RuntimeProviderInvocationResult value =
        result == null
            ? new RuntimeProviderInvocationResult(0, 0, null, null, null, null, null, null, "{}")
            : result;
    validateTransitionTarget(target, null, null, value.errorCode(), value.errorSummary());
    validate(value);
    transactions.executeWithoutResult(
        status -> {
          InvocationState row =
              jdbc.sql(
                      """
SELECT i.status,n.execution_id,i.execution_node_id FROM ai_runtime_provider_invocation i
JOIN ai_runtime_execution_node n ON n.id=i.execution_node_id
WHERE i.id=:id FOR UPDATE
""")
                  .param("id", invocationId)
                  .query(InvocationState.class)
                  .optional()
                  .orElseThrow(
                      () -> new IllegalArgumentException("Provider invocation does not exist"));
          RuntimeProviderInvocationStatus current =
              RuntimeProviderInvocationStatus.valueOf(row.status());
          states.requireTransition(current, target);
          jdbc.sql(
                  """
UPDATE ai_runtime_provider_invocation SET status=:target,
  input_units=:inputUnits,output_units=:outputUnits,
  estimated_cost=:estimatedCost,currency=:currency,latency_ms=:latencyMs,
  provider_request_id=:providerRequestId,error_code=:errorCode,error_summary=:errorSummary,
  metadata=JSON_MERGE_PATCH(metadata,CAST(:metadata AS JSON)),
  completed_at=CASE WHEN :terminal THEN CURRENT_TIMESTAMP(6) ELSE NULL END
WHERE id=:id
""")
              .param("target", target.name())
              .param("inputUnits", value.inputUnits())
              .param("outputUnits", value.outputUnits())
              .param("estimatedCost", value.estimatedCost())
              .param("currency", value.currency())
              .param("latencyMs", value.latencyMs())
              .param("providerRequestId", value.providerRequestId())
              .param("errorCode", value.errorCode())
              .param("errorSummary", value.errorSummary())
              .param("metadata", value.metadataJson())
              .param("terminal", target.terminal())
              .param("id", invocationId)
              .update();
          appendInTransaction(
              row.executionId(),
              row.executionNodeId(),
              "PROVIDER_INVOCATION_STATUS_CHANGED",
              objectPayload("from", current.name(), "to", target.name()));
        });
  }

  /**
   * 在独立事务中追加执行追踪事件。
   * 实现上，在事务边界内完成相关写操作，避免只更新部分数据。
   *
   * @param executionId 执行记录 ID
   * @param nodeId 执行节点 ID
   * @param eventType 写入追踪表的事件类型
   * @param payloadJson payload 的 JSON 文本
   * @return 处理后的业务结果
   */
  @Override
  public long appendEvent(long executionId, Long nodeId, String eventType, String payloadJson) {
    validateEvent(eventType, payloadJson);
    Long sequence =
        transactions.execute(
            status -> appendInTransaction(executionId, nodeId, eventType, payloadJson));
    if (sequence == null) throw new IllegalStateException("Event transaction returned no sequence");
    return sequence;
  }

  /**
   * 使用现有事务写入追踪事件。
   * 实现上，在事务边界内完成相关写操作，避免只更新部分数据；使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
   *
   * @param executionId 执行记录 ID
   * @param nodeId 执行节点 ID
   * @param eventType 写入追踪表的事件类型
   * @param payloadJson payload 的 JSON 文本
   * @return 处理后的业务结果
   */
  private long appendInTransaction(
      long executionId, Long nodeId, String eventType, String payloadJson) {
    validateEvent(eventType, payloadJson);
    long sequence =
        jdbc.sql(
                """
                SELECT next_event_sequence FROM ai_runtime_execution WHERE id=:id FOR UPDATE
                """)
            .param("id", executionId)
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("Execution does not exist"));
    jdbc.sql(
            "UPDATE ai_runtime_execution SET next_event_sequence=next_event_sequence+1 WHERE"
                + " id=:id")
        .param("id", executionId)
        .update();
    jdbc.sql(
            """
            INSERT INTO ai_runtime_execution_event(
              execution_id,execution_node_id,sequence_no,event_type,event_payload)
            VALUES (:executionId,:nodeId,:sequence,:eventType,:payload)
            """)
        .param("executionId", executionId)
        .param("nodeId", nodeId)
        .param("sequence", sequence)
        .param("eventType", eventType)
        .param("payload", payloadJson)
        .update();
    return sequence;
  }

  // 校验定义及其关联配置。
  private void validate(CreateRuntimeExecution command) {
    if (command == null
        || !externalId(command.executionId(), 64)
        || !externalId(command.traceId(), 80)
        || command.userId() <= 0
        || command.runtimeType() == null)
      throw new IllegalArgumentException("Execution identity is invalid");
    text(command.sessionId(), 80);
    text(command.goalSummary(), 1000);
    text(command.inputReference(), 255);
    text(command.workflowKey(), 100);
    text(command.workflowVersion(), 32);
    object(command.metadataJson(), "metadata");
  }

  // 校验定义及其关联配置。
  private void validate(CreateRuntimeExecutionNode command) {
    if (command == null
        || command.executionId() <= 0
        || command.nodeType() == null
        || command.nodeKey() == null
        || !NODE_KEY.matcher(command.nodeKey()).matches()
        || command.attempt() <= 0)
      throw new IllegalArgumentException("Execution node identity is invalid");
    boolean noSkill =
        command.skillId() == null
            && command.skillVersionId() == null
            && command.skillKeySnapshot() == null
            && command.skillVersionSnapshot() == null;
    boolean fullSkill =
        command.skillId() != null
            && command.skillVersionId() != null
            && command.skillKeySnapshot() != null
            && command.skillVersionSnapshot() != null;
    if (!noSkill && !fullSkill)
      throw new IllegalArgumentException("Execution node skill snapshot is incomplete");
    text(command.inputReference(), 255);
    digest(command.inputDigest());
    object(command.metadataJson(), "metadata");
  }

  // 校验定义及其关联配置。
  private void validate(CreateRuntimeProviderInvocation command) {
    if (command == null
        || !externalId(command.invocationId(), 64)
        || command.executionNodeId() <= 0
        || command.providerId() <= 0
        || command.providerModelId() <= 0
        || command.providerKeySnapshot() == null
        || !PROVIDER_KEY.matcher(command.providerKeySnapshot()).matches()
        || command.modelKeySnapshot() == null
        || !MODEL_KEY.matcher(command.modelKeySnapshot()).matches()
        || command.credentialSource() == null
        || command.attempt() <= 0)
      throw new IllegalArgumentException("Provider invocation identity is invalid");
    object(command.metadataJson(), "metadata");
  }

  // 校验定义及其关联配置。
  private void validate(RuntimeProviderInvocationResult result) {
    if (result.inputUnits() < 0
        || result.outputUnits() < 0
        || result.estimatedCost() != null && result.estimatedCost().signum() < 0
        || result.latencyMs() != null && result.latencyMs() < 0)
      throw new IllegalArgumentException("Provider invocation usage is invalid");
    boolean noCost = result.estimatedCost() == null && result.currency() == null;
    boolean priced =
        result.estimatedCost() != null
            && result.currency() != null
            && result.currency().matches("^[A-Z]{3}$");
    if (!noCost && !priced)
      throw new IllegalArgumentException("Provider invocation cost is invalid");
    text(result.providerRequestId(), 160);
    object(result.metadataJson(), "metadata");
  }

  /**
   * 校验定义及其关联配置。
   * 实现上，状态变化先经过状态机约束，阻止非法跳转。
   *
   * @param target 准备流转到的目标状态
   * @param reference 结果或输出的外部引用
   * @param digest 用于完整性检查的内容摘要
   * @param errorCode 失败时记录的业务错误码
   * @param errorSummary 便于追踪的失败摘要
   */
  private void validateTransitionTarget(
      Enum<?> target, String reference, String digest, String errorCode, String errorSummary) {
    if (target == null) throw new IllegalArgumentException("Transition target is required");
    boolean failed = "FAILED".equals(target.name()) || "TIMEOUT".equals(target.name());
    if (failed != (errorCode != null))
      throw new IllegalArgumentException("Failure transition error code is invalid");
    if (errorCode != null && !ERROR_CODE.matcher(errorCode).matches())
      throw new IllegalArgumentException("Error code is invalid");
    text(reference, 255);
    text(errorSummary, 500);
    digest(digest);
  }

  // 校验定义及其关联配置。
  private void validateEvent(String eventType, String payloadJson) {
    if (eventType == null || !EVENT_TYPE.matcher(eventType).matches())
      throw new IllegalArgumentException("Event type is invalid");
    object(payloadJson, "event payload");
  }

  // 把 JSON 文本解析为对象节点。通过 Jackson 完成 JSON 的解析或序列化。
  private void object(String value, String field) {
    try {
      JsonNode node = json.readTree(value);
      if (node == null || !node.isObject())
        throw new IllegalArgumentException(field + " must be an object");
      rejectSensitiveFields(node, field);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException(field + " is invalid");
    }
  }

  // 递归拒绝参数中的敏感字段。
  private void rejectSensitiveFields(JsonNode node, String field) {
    if (node.isObject())
      node.properties()
          .forEach(
              entry -> {
                String normalized = entry.getKey().replace("_", "").replace("-", "").toLowerCase();
                if (FORBIDDEN_JSON_FIELDS.contains(normalized))
                  throw new IllegalArgumentException(
                      field + " contains forbidden raw or secret data");
                rejectSensitiveFields(entry.getValue(), field);
              });
    else if (node.isArray()) node.forEach(child -> rejectSensitiveFields(child, field));
  }

  /**
   * 把 JSON 文本解析为对象节点。
   * 实现上，通过 Jackson 完成 JSON 的解析或序列化。
   *
   * @param firstKey first 的业务唯一键
   * @param firstValue 合并时优先采用的值
   * @param secondKey second 的业务唯一键
   * @param secondValue 第一项为空时使用的备用值
   * @return 处理后的业务结果
   */
  private String objectPayload(
      String firstKey, String firstValue, String secondKey, String secondValue) {
    return json.createObjectNode().put(firstKey, firstValue).put(secondKey, secondValue).toString();
  }

  private boolean externalId(String value, int max) {
    return value != null && value.length() <= max && EXTERNAL_ID.matcher(value).matches();
  }

  private void text(String value, int max) {
    if (value != null && value.length() > max)
      throw new IllegalArgumentException("Trace field is too long");
  }

  private void digest(String value) {
    if (value != null && !DIGEST.matcher(value).matches())
      throw new IllegalArgumentException("Trace digest is invalid");
  }

  private record NodeState(long executionId, String status) {}

  private record InvocationState(String status, long executionId, long executionNodeId) {}
}
