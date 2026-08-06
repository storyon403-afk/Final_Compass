package cn.finalscompass.ai.prompt;

import java.time.LocalDateTime;


/**
 * AI Prompt模板对象
 *
 * 对应数据库：
 *
 * ai_prompt_template
 *
 * 使用 JdbcTemplate 映射
 * 不使用 JPA
 */
public class AiPromptTemplate {


    /**
     * 主键
     */
    private Long id;



    /**
     * Skill ID
     *
     * 示例:
     *
     * complete-solution
     * course-answer
     * document-summary
     */
    private String skillId;



    /**
     * Prompt版本
     *
     * 示例:
     *
     * v1
     * v2
     */
    private String version;



    /**
     * 系统提示词
     *
     * 给LLM的system message
     */
    private String systemPrompt;



    /**
     * 输出约束
     *
     * 例如：
     *
     * JSON格式
     * 必须包含步骤
     * 必须返回置信度
     */
    private String outputContract;



    /**
     * 是否启用
     */
    private Boolean enabled;



    /**
     * 创建时间
     */
    private LocalDateTime createdAt;



    public Long getId() {
        return id;
    }



    public void setId(Long id) {
        this.id = id;
    }



    public String getSkillId() {
        return skillId;
    }



    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }



    public String getVersion() {
        return version;
    }



    public void setVersion(String version) {
        this.version = version;
    }



    public String getSystemPrompt() {
        return systemPrompt;
    }



    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }



    public String getOutputContract() {
        return outputContract;
    }



    public void setOutputContract(String outputContract) {
        this.outputContract = outputContract;
    }



    public Boolean getEnabled() {
        return enabled;
    }



    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }



    public LocalDateTime getCreatedAt() {
        return createdAt;
    }



    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }



    @Override
    public String toString() {

        return "AiPromptTemplate{" +
                "id=" + id +
                ", skillId='" + skillId + '\'' +
                ", version='" + version + '\'' +
                ", enabled=" + enabled +
                '}';

    }


}