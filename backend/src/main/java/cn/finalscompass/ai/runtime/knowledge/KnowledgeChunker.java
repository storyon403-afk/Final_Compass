package cn.finalscompass.ai.runtime.knowledge;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public final class KnowledgeChunker {
    private static final int TARGET=1800;
    public List<Chunk> chunk(String markdown){
        if(markdown==null||markdown.isBlank())throw new IllegalArgumentException("Knowledge Markdown is empty");
        List<Chunk> result=new ArrayList<>();String heading=null;int cursor=0,index=0;
        while(cursor<markdown.length()){
            int end=Math.min(markdown.length(),cursor+TARGET);if(end<markdown.length()){int boundary=markdown.lastIndexOf("\n\n",end);if(boundary>cursor+400)end=boundary;}
            String value=markdown.substring(cursor,end).trim();
            for(String line:value.split("\n"))if(line.matches("^#{1,6}\\s+.+")){heading=line.replaceFirst("^#{1,6}\\s+","").trim();break;}
            if(!value.isBlank())result.add(new Chunk(index++,heading,value,cursor,end,Math.max(1,(value.length()+3)/4)));
            cursor=end;while(cursor<markdown.length()&&Character.isWhitespace(markdown.charAt(cursor)))cursor++;
        }
        if(result.isEmpty())throw new IllegalArgumentException("Knowledge Markdown has no content");return List.copyOf(result);
    }
    public record Chunk(int index,String heading,String content,int start,int end,int tokenEstimate){}
}
