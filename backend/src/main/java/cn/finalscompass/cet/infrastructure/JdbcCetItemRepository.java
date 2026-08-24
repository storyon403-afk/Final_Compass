package cn.finalscompass.cet.infrastructure;

import cn.finalscompass.cet.domain.CetItemRepository;
import cn.finalscompass.controller.CetController.ItemInput;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcCetItemRepository implements CetItemRepository {
  private final JdbcClient jdbc;
  public JdbcCetItemRepository(JdbcClient jdbc){this.jdbc=jdbc;}
  @Override public String paperLevel(long id){return jdbc.sql("SELECT level FROM cet_paper WHERE id=:id")
      .param("id",id).query(String.class).optional().orElseThrow(this::notFound);}
  @Override public boolean sectionExists(long paper,String mode,String section){return jdbc.sql(
      "SELECT COUNT(*) FROM cet_paper_section WHERE paper_id=:paper AND mode=:mode AND section=:section")
      .param("paper",paper).param("mode",mode).param("section",section).query(Integer.class).single()>0;}
  @Override public long createItem(ItemInput input){
    jdbc.sql("""
        INSERT INTO cet_item(paper_id,mode,section,title,prompt,passage,translation,analysis,
          key_sentence,answer_type,options_json,correct_answer,item_order,audio_start_ms,audio_end_ms)
        VALUES (:paper,:mode,:section,:title,:prompt,:passage,:translation,:analysis,
          :keySentence,:answerType,CAST(:options AS JSON),:answer,:itemOrder,:startMs,:endMs)
        """).param("paper",input.paperId()).param("mode",input.mode()).param("section",input.section().trim())
        .param("title",input.title().trim()).param("prompt",input.prompt()).param("passage",input.passage())
        .param("translation",input.translation()).param("analysis",input.analysis()).param("keySentence",input.keySentence())
        .param("answerType",input.answerType()).param("options",options(input.optionsJson()))
        .param("answer",input.correctAnswer()).param("itemOrder",input.itemOrder())
        .param("startMs",input.audioStartMs()).param("endMs",input.audioEndMs()).update();
    return jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
  }
  @Override public ItemIdentity itemIdentity(long id){return jdbc.sql(
      "SELECT paper_id,mode,section FROM cet_item WHERE id=:id").param("id",id)
      .query(ItemIdentity.class).optional().orElseThrow(this::notFound);}
  @Override public void updateItem(long id,ItemInput input){
    int changed=jdbc.sql("""
        UPDATE cet_item SET paper_id=:paper,mode=:mode,section=:section,title=:title,prompt=:prompt,
          passage=:passage,translation=:translation,analysis=:analysis,key_sentence=:keySentence,
          answer_type=:answerType,options_json=CAST(:options AS JSON),correct_answer=:answer,
          item_order=:itemOrder,audio_start_ms=:startMs,audio_end_ms=:endMs WHERE id=:id
        """).param("paper",input.paperId()).param("mode",input.mode()).param("section",input.section().trim())
        .param("title",input.title().trim()).param("prompt",input.prompt()).param("passage",input.passage())
        .param("translation",input.translation()).param("analysis",input.analysis()).param("keySentence",input.keySentence())
        .param("answerType",input.answerType()).param("options",options(input.optionsJson()))
        .param("answer",input.correctAnswer()).param("itemOrder",input.itemOrder())
        .param("startMs",input.audioStartMs()).param("endMs",input.audioEndMs()).param("id",id).update();
    if(changed==0)throw notFound();
  }
  @Override public boolean paperExists(long id){return jdbc.sql("SELECT COUNT(*) FROM cet_paper WHERE id=:id")
      .param("id",id).query(Integer.class).single()>0;}
  @Override public String replacePracticeAudio(long id,String storage,String original,String mime){
    String previous=jdbc.sql("SELECT storage_name FROM cet_practice_audio WHERE paper_id=:id")
        .param("id",id).query(String.class).optional().orElse(null);
    jdbc.sql("""
        INSERT INTO cet_practice_audio(paper_id,storage_name,original_name,mime_type)
        VALUES (:id,:storage,:original,:mime)
        ON DUPLICATE KEY UPDATE storage_name=:storage,original_name=:original,mime_type=:mime
        """).param("id",id).param("storage",storage).param("original",original).param("mime",mime).update();return previous;
  }
  @Override public boolean itemExists(long id){return jdbc.sql("SELECT COUNT(*) FROM cet_item WHERE id=:id")
      .param("id",id).query(Integer.class).single()>0;}
  @Override public String replaceItemAudio(long id,String storage,String original,String mime){
    String previous=jdbc.sql("SELECT audio_storage_name FROM cet_item WHERE id=:id")
        .param("id",id).query(String.class).optional().orElse(null);
    jdbc.sql("UPDATE cet_item SET audio_storage_name=:storage,audio_original_name=:original,audio_mime_type=:mime WHERE id=:id")
        .param("storage",storage).param("original",original).param("mime",mime).param("id",id).update();return previous;
  }
  @Override public AudioDescriptor audio(long id){return jdbc.sql("""
      SELECT CASE WHEN i.mode='INTENSIVE' OR i.section='LISTENING_PASSAGE'
        THEN COALESCE(i.audio_storage_name,pa.storage_name) ELSE NULL END storage_name,
        CASE WHEN i.mode='INTENSIVE' OR i.section='LISTENING_PASSAGE'
        THEN COALESCE(i.audio_original_name,pa.original_name) ELSE NULL END original_name,
        COALESCE(i.audio_mime_type,pa.mime_type) mime_type
      FROM cet_item i LEFT JOIN cet_practice_audio pa ON pa.paper_id=i.paper_id WHERE i.id=:id
      """).param("id",id).query(AudioDescriptor.class).optional().orElseThrow(this::notFound);}
  private String options(String value){return value==null||value.isBlank()?"null":value;}
  private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"题库内容不存在");}
}
