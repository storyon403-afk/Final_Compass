package cn.finalscompass.ai.prompt;

import java.time.LocalDateTime;

/**
 * AI Prompt模板对象
 *
 * 对应数据库表：
 * ai_prompt_template
 *
 * 不使用JPA注解。
 * 当前项目使用JdbcTemplate。
 */
public class AiPromptTemplate {


    private Long id;


    /**
     * 对应Skill ID
     *
     * 例如：
     * math-proof-solver
     */
    private String skillId;


    /**
     * Prompt版本
     *
     * 例如：
     * v1
     */
    private String version;


    /**
     * 系统提示词
     */
    private String systemPrompt;


    /**
     * 输出约束
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

}