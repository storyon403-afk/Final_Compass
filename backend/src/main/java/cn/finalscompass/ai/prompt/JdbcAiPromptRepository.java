package cn.finalscompass.ai.prompt;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;



@Repository
public class JdbcAiPromptRepository 
        implements AiPromptRepository {


    private final JdbcTemplate jdbcTemplate;


    public JdbcAiPromptRepository(
            JdbcTemplate jdbcTemplate
    ){

        this.jdbcTemplate = jdbcTemplate;

    }



    @Override
    public AiPromptTemplate findActivePrompt(
            String skillId,
            String version
    ){


        String sql = """
                SELECT
                    id,
                    skill_id,
                    version,
                    system_prompt,
                    output_contract,
                    enabled,
                    created_at

                FROM ai_prompt_template

                WHERE skill_id = ?
                AND version = ?
                AND enabled = true

                LIMIT 1
                """;


        return jdbcTemplate.query(
                sql,

                new PromptRowMapper(),

                skillId,
                version

        )
        .stream()
        .findFirst()
        .orElse(null);

    }

}