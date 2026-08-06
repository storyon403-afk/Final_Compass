package cn.finalscompass.ai.prompt;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class JdbcAiPromptRepositoryTest {


    @Autowired
    private JdbcAiPromptRepository repository;



    @Test
    void shouldLoadPrompt(){


        AiPromptTemplate prompt =
                repository
                .findActiveBySkillId(
                    "complete-solution"
                )
                .orElse(null);



        assertNotNull(prompt);


        assertEquals(
                "complete-solution",
                prompt.getSkillId()
        );


        assertNotNull(
                prompt.getSystemPrompt()
        );


    }

}