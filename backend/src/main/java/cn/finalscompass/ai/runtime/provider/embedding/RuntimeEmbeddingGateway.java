package cn.finalscompass.ai.runtime.provider.embedding;

import cn.finalscompass.ai.runtime.provider.ProviderSelectionRequest;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderCandidate;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderMatcher;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderType;
import cn.finalscompass.service.AiCredentialResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/*
 *                 RuntimeEmbeddingGateway
 *                           │
 *                           ▼
 *              RuntimeProviderMatcher
 *              匹配 Embedding 候选模型
 *                           │
 *                           ▼
 *               读取模型 Embedding 配置
 *               Adapter Key + 向量维度
 *                           │
 *             ┌─────────────┴─────────────┐
 *             ▼                           ▼
 *    AiCredentialResolver      RuntimeEmbeddingClientRegistry
 *       解析平台 API Key             找到对应 Adapter
 *             │                           │
 *             └─────────────┬─────────────┘
 *                           ▼
 *                    调用 Embedding
 *                           │
 *             ┌─────────────┴─────────────┐
 *             ▼                           ▼
 *        调用成功并返回              调用失败，尝试下一个候选
 */
/**
 * 统一编排 Embedding 模型匹配、凭据解析、客户端选择和失败回退。
 * 维护入口：候选筛选改 RuntimeProviderMatcher；供应商报文改对应 EmbeddingClient。
 */
@Component
public final class RuntimeEmbeddingGateway {
  // 声明
  private final RuntimeProviderMatcher providers;
  private final RuntimeEmbeddingClientRegistry clients;
  private final AiCredentialResolver credentials;
  private final ObjectMapper json;

  // 注入
  public RuntimeEmbeddingGateway(
      RuntimeProviderMatcher providers,
      RuntimeEmbeddingClientRegistry clients,
      AiCredentialResolver credentials,
      ObjectMapper json) {
    this.providers = providers;
    this.clients = clients;
    this.credentials = credentials;
    this.json = json;
  }

  // 整个类真正的入口：匹配可用的 Embedding 模型，依次尝试调用，直到有候选成功返回向量
  public EmbeddingBatch embed(List<String> inputs) {
    // 只匹配支持 EMBEDDING 能力、API 类型并且可以使用平台凭据的候选模型
    List<RuntimeProviderCandidate> candidates =
        providers.match(
            new ProviderSelectionRequest(
                Set.of("EMBEDDING"),
                0,
                0,
                false,
                false,
                Set.of(RuntimeProviderType.API),
                Set.of(),
                "PLATFORM"));

    // 记录最后一次失败原因；当前候选失败后继续尝试下一个候选
    RuntimeException last = null;
    for (var candidate : candidates) {
      try {
        // 从模型配置中读取使用哪个 Embedding Adapter，以及这个模型应该返回的向量维度
        var config = json.readTree(candidate.model().configurationJson());
        String adapter = config.path("embeddingAdapterKey").asText();
        int expected = config.path("embeddingDimension").asInt(0);

        // 配置不完整的候选不能执行 Embedding，直接跳过
        if (adapter.isBlank() || expected < 1) continue;

        // 解析平台凭据，调用对应 Adapter，并在使用完毕后自动关闭凭据对象
        try (var credential =
            credentials.resolvePlatformService(
                candidate.provider().key(), candidate.model().key())) {
          List<float[]> vectors = clients.require(adapter).embed(candidate, credential, inputs);

          // 返回向量的实际维度必须和模型配置一致，防止错误向量进入知识库
          if (vectors.stream().anyMatch(vector -> vector.length != expected)) {
            throw new IllegalStateException("Embedding dimension mismatch");
          }

          return new EmbeddingBatch(
              candidate.provider().key(), candidate.model().key(), expected, vectors);
        }
      } catch (RuntimeException exception) {
        last = exception;
      } catch (Exception exception) {
        // JSON 解析、凭据关闭等受检异常统一包装成运行时异常
        last = new IllegalStateException(exception);
      }
    }

    // 没有候选或所有候选都失败时，把最后一次异常作为 cause 保留下来，便于排查
    throw new IllegalStateException("No Embedding Provider completed the request", last);
  }

  // Embedding 调用结果：实际使用的 Provider、Model、向量维度以及与输入顺序一致的向量列表
  public record EmbeddingBatch(
      String providerKey, String modelKey, int dimension, List<float[]> vectors) {
    public EmbeddingBatch {
      // 复制最外层 List，避免调用方在结果创建后增删向量元素
      vectors = List.copyOf(vectors);
    }
  }
}
