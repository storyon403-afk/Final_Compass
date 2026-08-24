package cn.finalscompass.circle.domain;

import cn.finalscompass.model.ApiModels.*;
import java.util.List;

/** 教师圈命令的持久化端口 */
public interface CircleCommandRepository {
  boolean publishedResource(String course,String teacher,long resourceId);
  boolean addThank(long resourceId,long anonymousUserId);
  int thanks(long resourceId);
  long lookupCourse(String slug);
  long lookupTeacher(String slug);
  void addResource(long teacherId,long courseId,long userId,String title,String type,String description,
      String original,String storage,String mime,long size);
  String nickname(long anonymousUserId);
  void addDiscussion(long teacherId,long courseId,long userId,Long parentId,String content);
  boolean teacherBelongsToCourse(long courseId,long teacherId);
  void saveGuide(long courseId,long teacherId,String content,String note,long editorId);
  void incorporateSubmissions(List<Long> ids,long courseId,long teacherId);
  void addGuideSubmission(long courseId,long teacherId,long authorId,String content);
}
