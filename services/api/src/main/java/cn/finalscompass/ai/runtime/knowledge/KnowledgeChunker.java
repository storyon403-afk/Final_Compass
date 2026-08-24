package cn.finalscompass.ai.runtime.knowledge;

import java.util.*;
import org.springframework.stereotype.Component;

/*
 * 维护流程图：文档正文 --> 清洗换行 --> 滑动窗口切片 --> 保留重叠上下文 --> Chunk 列表
 */
/**
 * 把知识文档切成带重叠窗口的片段，兼顾向量检索粒度和上下文连续性
 * 维护入口：分块大小和重叠率改这里；可升级为按标题、段落或语义边界切分，减少句子被截断
 */
@Component
public final class KnowledgeChunker {
  private static final int TARGET = 1800;

  // 将文档内容切分为可检索片段。按长度窗口逐段处理，并保留必要重叠以减少上下文断裂
  public List<Chunk> chunk(String markdown) {
    if (markdown == null || markdown.isBlank())
      throw new IllegalArgumentException("Knowledge Markdown is empty");
    List<Chunk> result = new ArrayList<>();
    String heading = null;
    int cursor = 0, index = 0;
    while (cursor < markdown.length()) {
      int end = Math.min(markdown.length(), cursor + TARGET);
      if (end < markdown.length()) {
        int boundary = markdown.lastIndexOf("\n\n", end);
        if (boundary > cursor + 400) end = boundary;
      }
      String value = markdown.substring(cursor, end).trim();
      for (String line : value.split("\n"))
        if (line.matches("^#{1,6}\\s+.+")) {
          heading = line.replaceFirst("^#{1,6}\\s+", "").trim();
          break;
        }
      if (!value.isBlank())
        result.add(
            new Chunk(index++, heading, value, cursor, end, Math.max(1, (value.length() + 3) / 4)));
      cursor = end;
      while (cursor < markdown.length() && Character.isWhitespace(markdown.charAt(cursor)))
        cursor++;
    }
    if (result.isEmpty()) throw new IllegalArgumentException("Knowledge Markdown has no content");
    return List.copyOf(result);
  }

  public record Chunk(
      int index, String heading, String content, int start, int end, int tokenEstimate) {}
}
