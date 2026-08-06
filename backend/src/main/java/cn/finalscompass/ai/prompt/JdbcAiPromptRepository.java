package cn.finalscompass.ai.prompt;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;


import java.util.Optional;



@Repository
public class JdbcAiPromptRepository {


    private final JdbcTemplate jdbcTemplate;


    public JdbcAiPromptRepository(
            JdbcTemplate jdbcTemplate
    ){

        this.jdbcTemplate = jdbcTemplate;

    }



    public Optional<AiPromptTemplate> findActiveBySkillId(
            String skillId
    ){


        String sql="""
        SELECT
            id,
            skill_id,
            version,
            system_prompt,
            output_contract,
            enabled,
            created_at

        FROM ai_prompt_template

        WHERE skill_id=?
        AND enabled=true

        ORDER BY id DESC

        LIMIT 1
        """;


        return jdbcTemplate.query(
                sql,
                new PromptRowMapper(),
                skillId
        )
        .stream()
        .findFirst();

    }

}