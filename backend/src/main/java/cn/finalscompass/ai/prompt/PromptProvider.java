package cn.finalscompass.ai.prompt;


/**
 * Prompt提供层。
 *
 * Skill不直接访问数据库。
 *
 * 调用链：
 *
 * Skill
 *   |
 * PromptProvider
 *   |
 * Repository
 *   |
 * Database
 *
 */
public interface PromptProvider {


    /**
     * 获取Prompt模板
     *
     * @param skillId Skill编号
     * @param version Prompt版本
     */
    AiPromptTemplate getPrompt(
            String skillId,
            String version
    );


}