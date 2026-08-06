package cn.finalscompass.ai.prompt;


/**
 * Prompt 提供接口
 *
 * 当前实现：
 * MySQL + JdbcTemplate
 *
 * 后续可以替换：
 * Redis
 * 配置中心
 * 远程Prompt服务
 */
public interface PromptProvider {


    /**
     * 根据 Skill 获取当前启用 Prompt
     *
     * @param skillId Skill ID
     * @return Prompt模板
     */
    AiPromptTemplate getPrompt(
            String skillId
    );


}