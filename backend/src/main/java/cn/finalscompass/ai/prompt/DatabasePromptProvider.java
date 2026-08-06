package cn.finalscompass.ai.prompt;


import org.springframework.stereotype.Component;



@Component
public class DatabasePromptProvider
        implements PromptProvider {



    private final JdbcAiPromptRepository repository;



    public DatabasePromptProvider(
            JdbcAiPromptRepository repository
    ){

        this.repository = repository;

    }



    @Override
    public AiPromptTemplate getPrompt(
            String skillId
    ){


        return repository
                .findActiveBySkillId(skillId)
                .orElseGet(
                        this::defaultPrompt
                );

    }




    private AiPromptTemplate defaultPrompt(){

        AiPromptTemplate template =
                new AiPromptTemplate();


        template.setSkillId(
                "default"
        );


        template.setVersion(
                "v1"
        );


        template.setSystemPrompt(
                "你是学习助手"
        );


        template.setOutputContract(
                "返回结构化答案"
        );


        template.setEnabled(true);


        return template;

    }

}
