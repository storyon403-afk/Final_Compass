package cn.finalscompass.ai.runtime.knowledge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeChunkerTest {
    @Test void producesBoundedOrderedChunksWithHeadingMetadata(){
        String markdown="# 第一章\n\n"+"内容与公式。".repeat(500)+"\n\n## 第二节\n\n"+"练习题。".repeat(300);
        var chunks=new KnowledgeChunker().chunk(markdown);
        assertTrue(chunks.size()>1);assertEquals(0,chunks.getFirst().index());
        for(int i=0;i<chunks.size();i++){assertEquals(i,chunks.get(i).index());assertTrue(chunks.get(i).end()>chunks.get(i).start());assertFalse(chunks.get(i).content().isBlank());}
        assertEquals("第一章",chunks.getFirst().heading());
    }
    @Test void rejectsEmptyKnowledge(){assertThrows(IllegalArgumentException.class,()->new KnowledgeChunker().chunk("  "));}
}
