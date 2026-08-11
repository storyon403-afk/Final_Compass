package cn.finalscompass.ai.runtime.knowledge;

import cn.finalscompass.ai.runtime.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class KnowledgeSearchRuntimeToolHandler implements RuntimeToolHandler {
    private final KnowledgeService knowledge;private final ObjectMapper json;
    public KnowledgeSearchRuntimeToolHandler(KnowledgeService knowledge,ObjectMapper json){this.knowledge=knowledge;this.json=json;}
    public String executorKey(){return "knowledge-search-v1";}
    public String invoke(RuntimeToolDefinition definition,RuntimeToolExecutionContext context,String argumentsJson){
        try{var input=json.readTree(argumentsJson);String query=input.path("query").asText("");int limit=input.path("limit").asInt(5);
            return json.writeValueAsString(Map.of("results",knowledge.search(context.userId(),context.knowledgeScope(),query,limit)));
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalStateException("Knowledge search failed",e);}
    }
}
