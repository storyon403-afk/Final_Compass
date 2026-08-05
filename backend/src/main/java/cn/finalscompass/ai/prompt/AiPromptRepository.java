package cn.finalscompass.ai.prompt;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.Optional;



@Repository
public interface AiPromptRepository
        extends JpaRepository<AiPromptTemplate,Long> {



    /**
     * 查询指定Skill版本的启用Prompt
     */
    Optional<AiPromptTemplate>
    findFirstBySkillIdAndVersionAndEnabledTrue(
            String skillId,
            String version
    );


}