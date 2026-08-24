package cn.finalscompass.ai.runtime.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 校验供应商、模型和端点定义之间的完整性、唯一性及安全约束
 * 维护入口：新增配置字段时必须同步补充这里的交叉校验，避免无效定义进入运行时
 */
@Component
public final class RuntimeProviderDefinitionValidator {
  private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$");
  private static final Pattern CAPABILITY = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
  private static final Set<String> CREDENTIAL_SOURCES =
      Set.of("PLATFORM", "STORED_BYOK", "EPHEMERAL_BYOK");
  private final ObjectMapper json;

  public RuntimeProviderDefinitionValidator(ObjectMapper json) {
    this.json = json;
  }

  // 校验定义及其关联配置
  // 可升级：该方法职责较多，后续可按校验、执行和结果持久化拆分
  public void validate(RuntimeProviderDefinition provider) {
    require(
        provider.id() > 0 && provider.key() != null && KEY.matcher(provider.key()).matches(),
        provider,
        "providerKey",
        "INVALID_KEY");
    require(
        provider.name() != null && !provider.name().isBlank(), provider, "name", "MISSING_FIELD");
    require(
        provider.status() != RuntimeProviderStatus.DISABLED, provider, "status", "NOT_ROUTABLE");
    require(
        provider.adapterKey() != null && KEY.matcher(provider.adapterKey()).matches(),
        provider,
        "adapterKey",
        "INVALID_KEY");
    object(provider, "credentialPolicy", provider.credentialPolicyJson());
    object(provider, "configuration", provider.configurationJson());
    require(
        !provider.supportedCredentialSources().isEmpty()
            && CREDENTIAL_SOURCES.containsAll(provider.supportedCredentialSources()),
        provider,
        "credentialPolicy",
        "INVALID_CREDENTIAL_SOURCE");
    require(!provider.endpoints().isEmpty(), provider, "endpoints", "NO_ROUTABLE_ENDPOINT");
    Set<Long> endpointIds = new HashSet<>();
    Set<String> endpointKeys = new HashSet<>();
    for (RuntimeProviderEndpoint endpoint : provider.endpoints()) {
      require(
          endpoint.id() > 0 && endpointIds.add(endpoint.id()) && endpointKeys.add(endpoint.key()),
          provider,
          "endpoints",
          "DUPLICATE_ENDPOINT");
      require(
          endpoint.status() != RuntimeProviderStatus.DISABLED
              && endpoint.priority() >= 0
              && endpoint.weight() >= 0
              && endpoint.connectTimeoutMs() >= 100
              && endpoint.requestTimeoutMs() >= endpoint.connectTimeoutMs(),
          provider,
          "endpoint." + endpoint.key(),
          "INVALID_ENDPOINT");
      require(
          validUrl(endpoint.baseUrl()), provider, "endpoint." + endpoint.key(), "INVALID_BASE_URL");
      object(
          provider, "endpoint." + endpoint.key() + ".configuration", endpoint.configurationJson());
    }
    Set<Long> modelIds = new HashSet<>();
    Set<String> modelKeys = new HashSet<>();
    for (RuntimeProviderModel model : provider.models()) {
      require(
          model.id() > 0 && modelIds.add(model.id()) && modelKeys.add(model.key()),
          provider,
          "models",
          "DUPLICATE_MODEL");
      require(
          model.status() == RuntimeProviderModelStatus.ACTIVE
              && model.routingPriority() >= 0
              && model.routingWeight() >= 0,
          provider,
          "model." + model.key(),
          "INVALID_MODEL");
      require(
          model.capabilities().stream().allMatch(value -> CAPABILITY.matcher(value).matches()),
          provider,
          "model." + model.key() + ".capabilities",
          "INVALID_CAPABILITY");
      object(provider, "model." + model.key() + ".configuration", model.configurationJson());
    }
  }

  // 校验外部 URL 的协议和格式
  private boolean validUrl(String value) {
    try {
      URI uri = URI.create(value);
      return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
          && uri.getHost() != null;
    } catch (Exception exception) {
      return false;
    }
  }

  // 把 JSON 文本解析为对象节点。通过 Jackson 完成 JSON 的解析或序列化
  private JsonNode object(RuntimeProviderDefinition provider, String field, String value) {
    try {
      JsonNode node = json.readTree(value);
      require(node != null && node.isObject(), provider, field, "INVALID_JSON");
      return node;
    } catch (InvalidRuntimeProviderDefinitionException exception) {
      throw exception;
    } catch (Exception exception) {
      throw invalid(provider, field, "INVALID_JSON");
    }
  }

  private void require(
      boolean condition, RuntimeProviderDefinition provider, String field, String reason) {
    if (!condition) throw invalid(provider, field, reason);
  }

  // 构造统一的参数校验异常
  private InvalidRuntimeProviderDefinitionException invalid(
      RuntimeProviderDefinition provider, String field, String reason) {
    return new InvalidRuntimeProviderDefinitionException(
        provider == null ? null : provider.key(), field, reason);
  }
}
