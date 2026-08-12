package cn.finalscompass.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {
  public static final int DAILY_LOGIN_POINTS = 1;
  public static final int RESOURCE_SUBMITTED_POINTS = 2;
  public static final int RESOURCE_APPROVED_POINTS = 5;
  public static final int CONTENT_APPROVED_POINTS = 2;
  private final JdbcClient jdbc;

  public ActivityService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void recordDailyLogin(long userId) {
    award(userId, "DAILY_LOGIN", DAILY_LOGIN_POINTS, "DAY", LocalDate.now().toString());
  }

  public void recordResourceSubmitted(long userId, String sourceRef) {
    award(userId, "RESOURCE_SUBMITTED", RESOURCE_SUBMITTED_POINTS, "RESOURCE", sourceRef);
  }

  public void recordApproved(long userId, String itemType, long itemId) {
    int points = "RESOURCE".equals(itemType) ? RESOURCE_APPROVED_POINTS : CONTENT_APPROVED_POINTS;
    award(userId, itemType + "_APPROVED", points, itemType, Long.toString(itemId));
  }

  public void award(
      long userId, String eventType, int points, String sourceType, String sourceRef) {
    jdbc.sql(
            """
INSERT IGNORE INTO activity_event(user_id,event_type,points,source_type,source_ref,event_date)
VALUES (:user,:event,:points,:sourceType,:sourceRef,CURRENT_DATE)
""")
        .param("user", userId)
        .param("event", eventType)
        .param("points", points)
        .param("sourceType", sourceType)
        .param("sourceRef", sourceRef)
        .update();
  }

  public List<Map<String, Object>> currentMonthLeaderboard() {
    return jdbc.sql(
            """
SELECT u.id user_id,u.display_name display_name,SUM(e.points) score,
       DENSE_RANK() OVER (ORDER BY SUM(e.points) DESC, MIN(e.occurred_at),u.id) ranking_position
FROM activity_event e JOIN app_user u ON u.id=e.user_id
WHERE e.event_date>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01')
  AND e.event_date<DATE_ADD(DATE_FORMAT(CURRENT_DATE,'%Y-%m-01'),INTERVAL 1 MONTH)
  AND u.active=TRUE
GROUP BY u.id,u.display_name
ORDER BY score DESC,MIN(e.occurred_at),u.id
LIMIT 20
""")
        .query()
        .listOfRows();
  }

  @Transactional
  public void ensureCurrentEntitlements() {
    LocalDate entitlementMonth = YearMonth.now().atDay(1);
    Integer existing =
        jdbc.sql("SELECT COUNT(*) FROM ai_monthly_entitlement WHERE entitlement_month=:month")
            .param("month", entitlementMonth)
            .query(Integer.class)
            .single();
    if (existing > 0) return;
    jdbc.sql(
            """
INSERT INTO ai_monthly_entitlement(entitlement_month,user_id,source_month,activity_score,ranking_position)
SELECT :month,ranked.user_id,DATE_SUB(:month,INTERVAL 1 MONTH),ranked.score,ranked.ranking_position
FROM (
  SELECT e.user_id,SUM(e.points) score,
         ROW_NUMBER() OVER (ORDER BY SUM(e.points) DESC,MIN(e.occurred_at),e.user_id) ranking_position
  FROM activity_event e JOIN app_user u ON u.id=e.user_id
  WHERE e.event_date>=DATE_SUB(:month,INTERVAL 1 MONTH)
    AND e.event_date<:month AND u.active=TRUE
  GROUP BY e.user_id
) ranked WHERE ranked.ranking_position<=20
""")
        .param("month", entitlementMonth)
        .update();
  }

  public boolean hasPlatformEntitlement(long userId) {
    ensureCurrentEntitlements();
    return jdbc.sql(
                "SELECT COUNT(*) FROM ai_monthly_entitlement WHERE entitlement_month=:month AND"
                    + " user_id=:user")
            .param("month", YearMonth.now().atDay(1))
            .param("user", userId)
            .query(Integer.class)
            .single()
        == 1;
  }

  public int currentMonthScore(long userId) {
    return jdbc.sql(
            """
            SELECT COALESCE(SUM(points),0) FROM activity_event
            WHERE user_id=:user AND event_date>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01')
              AND event_date<DATE_ADD(DATE_FORMAT(CURRENT_DATE,'%Y-%m-01'),INTERVAL 1 MONTH)
            """)
        .param("user", userId)
        .query(Integer.class)
        .single();
  }
}
