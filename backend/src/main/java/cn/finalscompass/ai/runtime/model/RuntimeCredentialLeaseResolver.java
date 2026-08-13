package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.credential.ResolvedAiCredential;

/**
 * 按当前供应商候选动态解析短生命周期凭据的函数式契约。
 * 维护入口：凭据来源选择由实现方负责；这里只调整模型网关需要的解析参数。
 */
@FunctionalInterface
public interface RuntimeCredentialLeaseResolver {
  ResolvedAiCredential resolve(RuntimeModelInvocationCommand command);
}
