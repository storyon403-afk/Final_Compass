package cn.finalscompass.ai.prompt;


import org.springframework.stereotype.Component;



@Component
public class DefaultPromptProvider 
        implements PromptProvider {


    private final AiPromptRepository repository;



    public DefaultPromptProvider(
            AiPromptRepository repository
    ){

        this.repository = repository;

    }



    @Override
    public AiPromptTemplate getPrompt(
            String skillId,
            String version
    ){


        return repository.findActivePrompt(
                skillId,
                version
        );


    }

}