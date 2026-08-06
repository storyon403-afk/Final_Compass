package cn.finalscompass.ai;


import cn.finalscompass.ai.prompt.AiPromptTemplate;
import cn.finalscompass.ai.prompt.PromptProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class AiPromptProviderTest {


    @Autowired
    private PromptProvider promptProvider;



    @Test
    void shouldProvidePrompt(){


        AiPromptTemplate prompt =
                promptProvider.getPrompt(
                        "complete-solution"
                );



        assertNotNull(prompt);



        System.out.println(
                prompt.getSystemPrompt()
        );



        assertTrue(
                prompt.getEnabled()
        );


    }

}