package cn.finalscompass.ai.prompt;


/**
 * Prompt获取抽象。
 *
 * Skill和数据库解耦。
 *
 * 后续可以接：
 *
 * 数据库
 * Redis
 * 配置中心
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