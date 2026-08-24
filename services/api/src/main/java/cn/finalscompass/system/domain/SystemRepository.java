package cn.finalscompass.system.domain;

import cn.finalscompass.model.ApiModels.Announcement;
import java.util.List;
import java.util.Map;

/** 系统管理与内容审核的持久化端口 */
public interface SystemRepository {
  void checkHealth();
  Announcement announcement();
  void updateAnnouncement(String content, boolean enabled, long adminId);
  Map<String, Object> metrics();
  List<Map<String, Object>> pendingModeration();
  void expireBetaRequests();
  List<Map<String, Object>> betaRequests();
  Long contributionOwner(String type, long id);
  int updateModerationStatus(String type, long id, String status);
  void recordModeration(String type, long id, String decision, long reviewerId);
  void markGuideReviewed(long id);
  int removeDiscussion(long id);
}
