package cn.finalscompass.cet.domain;

import cn.finalscompass.controller.CetController.CetItem;
import cn.finalscompass.controller.CetController.CetPaper;
import cn.finalscompass.controller.CetController.SectionResource;
import java.util.List;

/** CET 模块读取模型的持久化端口 */
public interface CetQueryRepository {
  List<CetPaper> findPublishedPapers(String level);
  List<CetItem> findPublishedItems(String level, String mode, String section);
  List<CetItem> findAllItems();
  List<SectionResource> findSections();
}
