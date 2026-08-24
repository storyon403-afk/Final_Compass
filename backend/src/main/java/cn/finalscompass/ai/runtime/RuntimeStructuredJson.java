package cn.finalscompass.ai.runtime;

/**
 * 从模型返回的混合文本中截取 JSON 对象或数组，供结构化结果解析使用
 * 维护入口：模型返回格式发生变化时修改提取规则；不要在这里加入具体供应商协议
 */
public final class RuntimeStructuredJson {
  private RuntimeStructuredJson() {}

  // 从模型文本中提取结构化 JSON
  public static String extract(String value) {
    if (value == null) return "";
    String text = value.trim();
    if (text.startsWith("```")) {
      int first = text.indexOf('\n');
      int last = text.lastIndexOf("```");
      if (first >= 0 && last > first) text = text.substring(first + 1, last).trim();
    }
    int object = text.indexOf('{'), array = text.indexOf('[');
    int start = object < 0 ? array : array < 0 ? object : Math.min(object, array);
    if (start > 0) text = text.substring(start);
    int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
    if (end >= 0 && end < text.length() - 1) text = text.substring(0, end + 1);
    return text.trim();
  }
}
