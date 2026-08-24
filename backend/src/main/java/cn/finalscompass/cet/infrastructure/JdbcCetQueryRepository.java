package cn.finalscompass.cet.infrastructure;

import cn.finalscompass.cet.domain.CetQueryRepository;
import cn.finalscompass.controller.CetController.CetItem;
import cn.finalscompass.controller.CetController.CetPaper;
import cn.finalscompass.controller.CetController.SectionResource;
import cn.finalscompass.shared.storage.UploadStorage;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCetQueryRepository implements CetQueryRepository {
  private final JdbcClient jdbc;
  private final UploadStorage storage;
  public JdbcCetQueryRepository(JdbcClient jdbc,UploadStorage storage){this.jdbc=jdbc;this.storage=storage;}

  @Override public List<CetPaper> findPublishedPapers(String level){
    String filter=level==null?"":"AND p.level=:level";
    var query=jdbc.sql("""
        SELECT p.id,p.level,p.exam_year,p.exam_month,p.set_number,p.title,p.published,
          a.source_name,a.source_page_url,a.usage_note,pa.original_name audio_original_name
        FROM cet_paper p LEFT JOIN cet_paper_asset a ON a.paper_id=p.id
        LEFT JOIN cet_practice_audio pa ON pa.paper_id=p.id
        WHERE p.published=TRUE %s ORDER BY p.exam_year DESC,p.exam_month DESC,p.set_number
        """.formatted(filter));
    if(level!=null)query=query.param("level",level);
    return query.query(PaperRow.class).list().stream().map(row->new CetPaper(
        row.id(),row.level(),row.examYear(),row.examMonth(),row.setNumber(),row.title(),row.published(),
        row.sourceName(),row.sourcePageUrl(),row.usageNote(),row.audioOriginalName(),audioExists(row.id()))).toList();
  }
  @Override public List<CetItem> findPublishedItems(String level,String mode,String section){
    String filter=section==null?"":"AND i.section=:section";
    var query=jdbc.sql("""
        SELECT i.id,i.paper_id,p.level,p.exam_year,p.exam_month,p.set_number,p.title paper_title,
          i.mode,i.section,i.title,i.prompt,i.passage,i.translation,i.analysis,i.key_sentence,
          i.answer_type,i.options_json,i.correct_answer,i.item_order,
          CASE WHEN i.mode='INTENSIVE' OR i.section='LISTENING_PASSAGE'
            THEN COALESCE(i.audio_original_name,pa.original_name) ELSE NULL END audio_original_name,
          i.audio_start_ms,i.audio_end_ms
        FROM cet_item i JOIN cet_paper p ON p.id=i.paper_id
        LEFT JOIN cet_practice_audio pa ON pa.paper_id=p.id
        WHERE p.published=TRUE AND p.level=:level AND i.mode=:mode %s
        ORDER BY p.exam_year DESC,p.exam_month DESC,p.set_number,i.item_order,i.id
        """.formatted(filter)).param("level",level).param("mode",mode);
    if(section!=null)query=query.param("section",section);
    return query.query(CetItem.class).list();
  }
  @Override public List<CetItem> findAllItems(){return jdbc.sql("""
      SELECT i.id,i.paper_id,p.level,p.exam_year,p.exam_month,p.set_number,p.title paper_title,
        i.mode,i.section,i.title,i.prompt,i.passage,i.translation,i.analysis,i.key_sentence,
        i.answer_type,i.options_json,i.correct_answer,i.item_order,
        CASE WHEN i.mode='INTENSIVE' OR i.section='LISTENING_PASSAGE'
          THEN COALESCE(i.audio_original_name,pa.original_name) ELSE NULL END audio_original_name,
        i.audio_start_ms,i.audio_end_ms
      FROM cet_item i JOIN cet_paper p ON p.id=i.paper_id
      LEFT JOIN cet_practice_audio pa ON pa.paper_id=p.id
      ORDER BY p.exam_year DESC,p.exam_month DESC,p.set_number,i.mode,i.section,i.item_order,i.id
      """).query(CetItem.class).list();}
  @Override public List<SectionResource> findSections(){return jdbc.sql("""
      SELECT s.id,s.paper_id,p.title paper_title,p.level,s.mode,s.section,COUNT(i.id) item_count
      FROM cet_paper_section s JOIN cet_paper p ON p.id=s.paper_id
      LEFT JOIN cet_item i ON i.paper_id=s.paper_id AND i.mode=s.mode AND i.section=s.section
      GROUP BY s.id,s.paper_id,p.title,p.level,s.mode,s.section
      ORDER BY p.exam_year DESC,p.exam_month DESC,p.set_number,s.mode,s.section
      """).query(SectionResource.class).list();}
  private boolean audioExists(long paperId){
    String name=jdbc.sql("SELECT storage_name FROM cet_practice_audio WHERE paper_id=:id")
        .param("id",paperId).query(String.class).optional().orElse(null);
    return name!=null&&storage.exists(name);
  }
  private record PaperRow(long id,String level,int examYear,int examMonth,int setNumber,String title,
      boolean published,String sourceName,String sourcePageUrl,String usageNote,String audioOriginalName){}
}
