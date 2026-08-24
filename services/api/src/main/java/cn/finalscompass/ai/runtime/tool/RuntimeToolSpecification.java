package cn.finalscompass.ai.runtime.tool;

/**
 * 提供给模型的工具公开规格，包含名称、说明和输入 Schema
 * 维护入口：模型可见的工具契约变化时修改构造来源；执行器内部配置不要暴露到这里
 */
public record RuntimeToolSpecification(
    String toolKey, String providerName, String description, String inputSchemaJson) {
  public RuntimeToolSpecification {
    if (toolKey == null
        || providerName == null
        || description == null
        || inputSchemaJson == null
        || !providerName.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))
      throw new IllegalArgumentException("Runtime Tool specification is invalid");
  }

  // 从数据库行构造领域对象
  public static RuntimeToolSpecification from(RuntimeToolDefinition definition) {
    String name = definition.toolKey().replaceAll("[^A-Za-z0-9_-]", "_");
    if (name.length() > 64) name = name.substring(0, 64);
    return new RuntimeToolSpecification(
        definition.toolKey(), name, definition.description(), definition.inputSchemaJson());
  }
}
