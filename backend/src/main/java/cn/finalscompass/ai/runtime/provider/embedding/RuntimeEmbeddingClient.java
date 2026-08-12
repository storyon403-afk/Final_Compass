package cn.finalscompass.ai.runtime.provider.embedding;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderCandidate;
import java.util.List;

public interface RuntimeEmbeddingClient {
  String adapterKey();

  List<float[]> embed(
      RuntimeProviderCandidate candidate, ResolvedAiCredential credential, List<String> inputs);
}
