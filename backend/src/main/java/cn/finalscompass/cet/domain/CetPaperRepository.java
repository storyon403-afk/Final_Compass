package cn.finalscompass.cet.domain;

import cn.finalscompass.controller.CetController.PaperInput;

/** CET 套卷与题型生命周期命令的持久化端口 */
public interface CetPaperRepository {
  void clearSection(long sectionId);
  long createPaper(String level, PaperInput input);
  void updatePaper(long paperId, String level, PaperInput input);
  void deletePaper(long paperId);
}
