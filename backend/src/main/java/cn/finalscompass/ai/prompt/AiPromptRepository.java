package cn.finalscompass.ai.prompt;


/**
 * Prompt数据访问抽象。
 *
 * 当前实现：
 *
 * JdbcTemplate
 *
 * 后续可以替换：
 *
 * Redis
 * 配置中心
 * 远程Prompt服务
 *
 */
public interface AiPromptRepository {


    /**
     * 查询指定Skill版本的启用Prompt
     *
     * @param skillId Skill编号
     * @param version Prompt版本
     */
    AiPromptTemplate findActivePrompt(
            String skillId,
            String version
    );


}