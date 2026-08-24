package cn.finalscompass.cet.infrastructure;

import cn.finalscompass.cet.domain.CetPaperRepository;
import cn.finalscompass.controller.CetController.PaperInput;
import cn.finalscompass.shared.storage.UploadStorage;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcCetPaperRepository implements CetPaperRepository {
  private static final Set<String> PRACTICE_SECTIONS=Set.of(
      "WRITING","LISTENING_PASSAGE","WORD_BANK","MATCHING","CAREFUL_READING","TRANSLATION");
  private final JdbcClient jdbc;private final UploadStorage storage;
  public JdbcCetPaperRepository(JdbcClient jdbc,UploadStorage storage){this.jdbc=jdbc;this.storage=storage;}

  @Override public void clearSection(long id){
    SectionKey key=jdbc.sql("SELECT paper_id,mode,section FROM cet_paper_section WHERE id=:id")
        .param("id",id).query(SectionKey.class).optional().orElseThrow(this::notFound);
    List<String> files=jdbc.sql("SELECT audio_storage_name FROM cet_item WHERE paper_id=:paper AND mode=:mode AND section=:section")
        .param("paper",key.paperId()).param("mode",key.mode()).param("section",key.section()).query(String.class).list();
    jdbc.sql("DELETE FROM cet_item WHERE paper_id=:paper AND mode=:mode AND section=:section")
        .param("paper",key.paperId()).param("mode",key.mode()).param("section",key.section()).update();
    files.stream().filter(value->value!=null&&!value.isBlank()).distinct().forEach(storage::deleteQuietly);
  }
  @Override public long createPaper(String level,PaperInput input){
    jdbc.sql("""
        INSERT INTO cet_paper(level,exam_year,exam_month,set_number,title,published)
        VALUES (:level,:year,:month,:setNumber,:title,TRUE)
        """).param("level",level).param("year",input.examYear()).param("month",input.examMonth())
        .param("setNumber",input.setNumber()).param("title",input.title().trim()).update();
    long id=jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
    saveSource(id,input);createSlots(id,level);return id;
  }
  @Override public void updatePaper(long id,String level,PaperInput input){
    String current=jdbc.sql("SELECT level FROM cet_paper WHERE id=:id").param("id",id)
        .query(String.class).optional().orElseThrow(this::notFound);
    if(!current.equals(level))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"已有套卷的考试级别不可修改");
    int changed=jdbc.sql("""
        UPDATE cet_paper SET level=:level,exam_year=:year,exam_month=:month,
          set_number=:setNumber,title=:title WHERE id=:id
        """).param("level",level).param("year",input.examYear()).param("month",input.examMonth())
        .param("setNumber",input.setNumber()).param("title",input.title().trim()).param("id",id).update();
    if(changed==0)throw notFound();saveSource(id,input);
  }
  @Override public void deletePaper(long id){
    List<String> files=jdbc.sql("""
        SELECT question_storage_name FROM cet_paper_asset WHERE paper_id=:id
        UNION ALL SELECT answer_storage_name FROM cet_paper_asset WHERE paper_id=:id
        UNION ALL SELECT storage_name FROM cet_practice_audio WHERE paper_id=:id
        UNION ALL SELECT audio_storage_name FROM cet_item WHERE paper_id=:id
        """).param("id",id).query(String.class).list();
    if(jdbc.sql("DELETE FROM cet_paper WHERE id=:id").param("id",id).update()==0)throw notFound();
    files.stream().filter(value->value!=null&&!value.isBlank()).distinct().forEach(storage::deleteQuietly);
  }
  private void saveSource(long id,PaperInput input){
    jdbc.sql("""
        INSERT INTO cet_paper_asset(paper_id,source_name,source_page_url,usage_note)
        VALUES (:id,:name,:url,:note)
        ON DUPLICATE KEY UPDATE source_name=:name,source_page_url=:url,usage_note=:note
        """).param("id",id).param("name",input.sourceName().trim()).param("url",input.sourcePageUrl().trim())
        .param("note",input.usageNote()==null?"":input.usageNote().trim()).update();
  }
  private void createSlots(long paperId,String level){
    for(String section:PRACTICE_SECTIONS)insertSlot(paperId,"PRACTICE",section);
    insertSlot(paperId,"INTENSIVE","LONG_CONVERSATION");insertSlot(paperId,"INTENSIVE","LISTENING_PASSAGE");
    insertSlot(paperId,"INTENSIVE",level.equals("CET4")?"NEWS":"LECTURE");
  }
  private void insertSlot(long paperId,String mode,String section){jdbc.sql(
      "INSERT IGNORE INTO cet_paper_section(paper_id,mode,section) VALUES (:paper,:mode,:section)")
      .param("paper",paperId).param("mode",mode).param("section",section).update();}
  private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"题库内容不存在");}
  private record SectionKey(long paperId,String mode,String section){}
}
