package cn.finalscompass.circle.domain;

import cn.finalscompass.model.ApiModels.*;
import java.time.LocalDate;
import java.util.List;

/** 教师圈页面读取模型的持久化端口 */
public interface CircleQueryRepository {
  List<Resource> resources(String course,String teacher);
  StoredFile resourceFile(String course,String teacher,long resourceId);
  void incrementDownloads(long resourceId);
  List<Discussion> discussions(String course,String teacher,LocalDate date);
  CircleSummary summary(String course,String teacher);
  StudyGuide guide(String course,String teacher);
  List<GuideSubmission> approvedSubmissions(String course,String teacher);
  record StoredFile(String storageName,String originalName,String mimeType){}
}
