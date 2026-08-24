package cn.finalscompass.ai.runtime.knowledge;

import cn.finalscompass.ai.runtime.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 把知识库检索封装成运行时工具，使模型能通过统一 Tool 协议查询资料
 * 维护入口：工具参数契约改 RuntimeToolDefinition；具体召回算法改 KnowledgeService
 */
@Component
public final class KnowledgeSearchRuntimeToolHandler implements RuntimeToolHandler {
  private final KnowledgeService knowledge;
  private final ObjectMapper json;

  public KnowledgeSearchRuntimeToolHandler(KnowledgeService knowledge, ObjectMapper json) {
    this.knowledge = knowledge;
    this.json = json;
  }

  public String executorKey() {
    return "knowledge-search-v1";
  }

  // 调用外部服务并解析返回结果。通过 Jackson 完成 JSON 的解析或序列化
  public String invoke(
      RuntimeToolDefinition definition, RuntimeToolExecutionContext context, String argumentsJson) {
    try {
      var input = json.readTree(argumentsJson);
      String query = input.path("query").asText("");
      int limit = input.path("limit").asInt(5);
      return json.writeValueAsString(
          Map.of(
              "results",
              knowledge.search(context.userId(), context.knowledgeScope(), query, limit)));
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Knowledge search failed", e);
    }
  }
}
