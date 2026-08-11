package cn.finalscompass.ai.runtime.tool;

public record RuntimeToolSpecification(
        String toolKey, String providerName, String description, String inputSchemaJson
) {
    public RuntimeToolSpecification {
        if (toolKey == null || providerName == null || description == null || inputSchemaJson == null
                || !providerName.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Runtime Tool specification is invalid");
    }

    public static RuntimeToolSpecification from(RuntimeToolDefinition definition) {
        String name = definition.toolKey().replaceAll("[^A-Za-z0-9_-]", "_");
        if (name.length() > 64) name = name.substring(0, 64);
        return new RuntimeToolSpecification(definition.toolKey(), name,
                definition.description(), definition.inputSchemaJson());
    }
}
