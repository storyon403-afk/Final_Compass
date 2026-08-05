package cn.finalscompass.ai.prompt;


import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;



public class PromptRowMapper 
        implements RowMapper<AiPromptTemplate> {


    @Override
    public AiPromptTemplate mapRow(
            ResultSet rs,
            int rowNum
    ) throws SQLException {


        AiPromptTemplate template =
                new AiPromptTemplate();


        template.setId(
                rs.getLong("id")
        );


        template.setSkillId(
                rs.getString("skill_id")
        );


        template.setVersion(
                rs.getString("version")
        );


        template.setSystemPrompt(
                rs.getString("system_prompt")
        );


        template.setOutputContract(
                rs.getString("output_contract")
        );


        template.setEnabled(
                rs.getBoolean("enabled")
        );


        template.setCreatedAt(
                rs.getTimestamp("created_at")
                .toLocalDateTime()
        );


        return template;

    }

}