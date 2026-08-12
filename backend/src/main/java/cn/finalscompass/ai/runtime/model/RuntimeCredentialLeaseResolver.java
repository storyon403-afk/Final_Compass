package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.credential.ResolvedAiCredential;

@FunctionalInterface
public interface RuntimeCredentialLeaseResolver {
  ResolvedAiCredential resolve(RuntimeModelInvocationCommand command);
}
