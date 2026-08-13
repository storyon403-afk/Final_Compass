package cn.finalscompass.ai.runtime.provider.embedding;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderCandidate;
import java.util.List;

/**
 * 运行时向量客户端的抽象契约，用于隔离业务编排与具体实现。
 * 维护入口：向量供应商协议或批量限制变化时修改这里。
 */
public interface RuntimeEmbeddingClient {
  String adapterKey();

  List<float[]> embed(
      RuntimeProviderCandidate candidate, ResolvedAiCredential credential, List<String> inputs);
}
