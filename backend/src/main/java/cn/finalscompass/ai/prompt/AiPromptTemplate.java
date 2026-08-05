package cn.finalscompass.ai.prompt;


import jakarta.persistence.*;

import java.time.LocalDateTime;


/**
 * AI Prompt模板实体。
 *
 * 一个Skill对应多个Prompt版本。
 *
 * 例如：
 *
 * math-proof-solver:v1
 * math-proof-solver:v2
 *
 */
@Entity
@Table(
        name = "ai_prompt_template"
)
public class AiPromptTemplate {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



    /**
     * 对应Skill ID
     *
     * 例如：
     *
     * math-proof-solver
     */
    @Column(
            nullable = false,
            length = 100
    )
    private String skillId;



    /**
     * Prompt版本
     *
     * v1
     * v2
     */
    @Column(
            nullable = false,
            length = 20
    )
    private String version;



    /**
     * 系统Prompt正文
     */
    @Lob
    @Column(
            nullable = false
    )
    private String systemPrompt;



    /**
     * 输出格式约束
     */
    @Lob
    private String outputContract;



    /**
     * 是否启用
     */
    @Column(
            nullable = false
    )
    private boolean enabled = true;



    /**
     * 创建时间
     */
    @Column(
            nullable = false
    )
    private LocalDateTime createdAt;



    @PrePersist
    public void onCreate(){

        if(createdAt == null){
            createdAt = LocalDateTime.now();
        }

    }



    protected AiPromptTemplate(){

    }



    public AiPromptTemplate(
            String skillId,
            String version,
            String systemPrompt,
            String outputContract
    ){

        this.skillId = skillId;
        this.version = version;
        this.systemPrompt = systemPrompt;
        this.outputContract = outputContract;
        this.enabled = true;

    }



    public Long getId() {
        return id;
    }


    public String getSkillId() {
        return skillId;
    }


    public String getVersion() {
        return version;
    }


    public String getSystemPrompt() {
        return systemPrompt;
    }


    public String getOutputContract() {
        return outputContract;
    }


    public boolean isEnabled() {
        return enabled;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


}