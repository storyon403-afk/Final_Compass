package cn.finalscompass.ai.runtime.chat;

import cn.finalscompass.ai.runtime.knowledge.KnowledgeService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds model prompts from bounded conversation history and optional knowledge results. */
@Component
public final class ChatPromptBuilder {
  private final ChatHistoryStore history;

  public ChatPromptBuilder(ChatHistoryStore history) {
    this.history = history;
  }

  public String systemInstruction(List<KnowledgeService.SearchResult> sources) {
    StringBuilder builder =
        new StringBuilder("你是 Finals Compass 的学习助手，只回答用户问题，不生成文件。回答使用简体中文，条理清晰。");
    if (sources == null || sources.isEmpty()) return builder.toString();
    builder.append("\n\n以下是从知识库检索到的资料，请优先依据资料回答，并在引用处标注对应编号 [n]；资料之外的内容请说明是通用知识。\n");
    int index = 1;
    for (KnowledgeService.SearchResult item : sources) {
      builder.append("\n[").append(index++).append("] ").append(nullSafe(item.title()));
      if (item.heading() != null && !item.heading().isBlank()) builder.append(" · ").append(item.heading());
      builder.append("\n").append(truncate(nullSafe(item.content()), 1200)).append("\n");
    }
    return builder.toString();
  }

  public String userPrompt(long userId, String sessionKey, String message) {
    List<Map<String, String>> items = history.load(userId, sessionKey);
    StringBuilder builder = new StringBuilder();
    if (!items.isEmpty()) {
      builder.append("## 对话历史\n");
      for (Map<String, String> item : items) {
        builder.append("user".equals(item.get("role")) ? "用户：" : "助手：")
            .append(nullSafe(item.get("content"))).append("\n");
      }
      builder.append("\n");
    }
    return builder.append("## 当前问题\n").append(message).toString();
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }
}
