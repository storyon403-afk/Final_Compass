package cn.finalscompass.ai.runtime.agent;

import cn.finalscompass.ai.runtime.knowledge.KnowledgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AiRuntimeDispatchService {
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final KnowledgeService knowledge;
  private final String fallbackUrl;
  private final String callbackBase;

  public AiRuntimeDispatchService(
      JdbcClient jdbc,
      ObjectMapper json,
      KnowledgeService knowledge,
      @Value("${app.ai.agent-gateway.url:http://127.0.0.1:8642}") String fallbackUrl,
      @Value(
              "${app.ai.agent-gateway.callback-base:http://127.0.0.1:8080/api/ai-center/external-agent}")
          String callbackBase) {
    this.jdbc = jdbc;
    this.json = json;
    this.knowledge = knowledge;
    this.fallbackUrl = fallbackUrl;
    this.callbackBase = callbackBase;
  }

  public Run start(long user, Start r) {
    if (r == null
        || r.goal() == null
        || r.goal().isBlank()
        || r.goal().length() > 20000
        || !Set.of("AGENT", "MULTI_WEB_AGENT").contains(r.runtimeType()))
      throw new IllegalArgumentException("Runtime request is invalid");
    if ("AGENT".equals(r.runtimeType())
        && "EPHEMERAL_BYOK".equals(r.credentialSource())
        && (r.ephemeralApiKey() == null || r.ephemeralApiKey().isBlank()))
      throw new IllegalArgumentException("Temporary API key is required");
    String key = UUID.randomUUID().toString();
    String status = "AGENT".equals(r.runtimeType()) ? "RUNNING" : "WAITING_EXTENSION";
    String token = "AGENT".equals(r.runtimeType()) ? UUID.randomUUID().toString() : null;
    jdbc.sql(
            "INSERT INTO"
                + " ai_runtime_run(run_key,user_id,runtime_type,goal,status,callback_token,request_payload)"
                + " VALUES(:key,:user,:type,:goal,:status,:token,CAST(:payload AS JSON))")
        .param("key", key)
        .param("user", user)
        .param("type", r.runtimeType())
        .param("goal", r.goal())
        .param("status", status)
        .param("token", token)
        .param(
            "payload",
            write(
                Map.of(
                    "credentialSource",
                    blank(r.credentialSource()),
                    "provider",
                    blank(r.provider()),
                    "model",
                    blank(r.model()),
                    "providers",
                    r.providers() == null ? List.of() : r.providers())))
        .update();
    if ("AGENT".equals(r.runtimeType())) return invokeAgent(user, key, r, token);
    seedParticipants(key, r.providers());
    return view(user, key);
  }

  private Run invokeAgent(long user, String key, Start r, String token) {
    try {
      String url =
          jdbc.sql(
                  "SELECT gateway_url FROM ai_agent_definition WHERE status='ACTIVE' ORDER BY id"
                      + " LIMIT 1")
              .query(String.class)
              .optional()
              .orElse(fallbackUrl);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("runId", key);
      body.put("goal", r.goal());
      body.put("provider", blank(r.provider()));
      body.put("model", blank(r.model()));
      body.put("ephemeralApiKey", r.ephemeralApiKey());
      body.put("knowledgeContext", knowledgeContext(user, r.goal()));
      body.put("allowWebSearch", Boolean.TRUE.equals(r.allowWebSearch()));
      body.put(
          "capabilities",
          List.of(
              "SKILL_RUNTIME",
              "KNOWLEDGE_SERVICE",
              "MCP_TOOLS",
              "BROWSER_GATEWAY",
              "DOCUMENT_GENERATION"));
      body.put("callbackBase", callbackBase);
      body.put("callbackToken", token);
      body.put("protocolVersion", "1.0");
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url + "/agent-runs"))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(write(body)))
              .build();
      HttpResponse<String> res =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(3))
              .build()
              .send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() / 100 != 2) throw new IllegalStateException();
    } catch (Exception e) {
      jdbc.sql(
              "UPDATE ai_runtime_run SET"
                  + " status='WAITING_CONFIGURATION',error_code='AGENT_GATEWAY_UNAVAILABLE' WHERE"
                  + " run_key=:key")
          .param("key", key)
          .update();
    }
    return view(user, key);
  }

  private String knowledgeContext(long user, String goal) {
    try {
      List<KnowledgeService.SearchResult> items = knowledge.search(user, null, goal, 5);
      if (items.isEmpty()) return "";
      StringBuilder out = new StringBuilder("请优先参考 Finals Compass 数据库检索结果：\n");
      for (int i = 0; i < items.size(); i++) {
        var item = items.get(i);
        out.append("\n[").append(i + 1).append("] ").append(item.title());
        if (item.heading() != null) out.append(" · ").append(item.heading());
        out.append("\n")
            .append(item.content(), 0, Math.min(item.content().length(), 1200))
            .append("\n");
      }
      return out.toString();
    } catch (Exception ignored) {
      return "";
    }
  }

  public long authenticateCallback(String runKey, String token) {
    if (runKey == null || token == null || token.isBlank())
      throw new SecurityException("Callback credential is missing");
    return jdbc.sql(
            "SELECT id FROM ai_runtime_run WHERE run_key=:key AND callback_token=:token AND"
                + " runtime_type='AGENT'")
        .param("key", runKey)
        .param("token", token)
        .query(Long.class)
        .optional()
        .orElseThrow(() -> new SecurityException("Callback credential is invalid"));
  }

  public void updateStatus(long runId, String status, String summary, String errorCode) {
    if (!Set.of("RUNNING", "COMPLETED", "FAILED", "WAITING_LOGIN").contains(status))
      throw new IllegalArgumentException("Callback status is invalid");
    String current =
        jdbc.sql("SELECT status FROM ai_runtime_run WHERE id=:run")
            .param("run", runId)
            .query(String.class)
            .single();
    if ("CANCELLED".equals(current)) return;
    if ("RUNNING".equals(status)) {
      jdbc.sql("UPDATE ai_runtime_run SET status='RUNNING',error_code=NULL WHERE id=:run")
          .param("run", runId)
          .update();
      return;
    }
    if ("WAITING_LOGIN".equals(status)) {
      jdbc.sql("UPDATE ai_runtime_run SET status='WAITING_LOGIN' WHERE id=:run")
          .param("run", runId)
          .update();
      return;
    }
    String finalStatus = "COMPLETED".equals(status) ? "COMPLETED" : "FAILED";
    jdbc.sql(
            "UPDATE ai_runtime_run SET status=:status,response_payload=CAST(:response AS"
                + " JSON),error_code=:error WHERE id=:run")
        .param("status", finalStatus)
        .param("response", write(Map.of("summary", summary == null ? "" : summary)))
        .param("error", errorCode)
        .param("run", runId)
        .update();
  }

  public long addArtifact(
      long runId, String fileName, String contentType, String storagePath, long size) {
    jdbc.sql(
            "INSERT INTO"
                + " ai_runtime_run_artifact(run_id,file_name,content_type,storage_path,size_bytes)"
                + " VALUES(:run,:name,:type,:path,:size)")
        .param("run", runId)
        .param("name", fileName)
        .param("type", contentType)
        .param("path", storagePath)
        .param("size", size)
        .update();
    return jdbc.sql(
            "SELECT id FROM ai_runtime_run_artifact WHERE run_id=:run ORDER BY id DESC LIMIT 1")
        .param("run", runId)
        .query(Long.class)
        .single();
  }

  public long ownerUserId(long runId) {
    return jdbc.sql("SELECT user_id FROM ai_runtime_run WHERE id=:run")
        .param("run", runId)
        .query(Long.class)
        .single();
  }

  public List<Map<String, Object>> artifacts(long user, String key) {
    return jdbc.sql(
            "SELECT a.id,a.file_name,a.content_type,a.size_bytes,a.created_at FROM"
                + " ai_runtime_run_artifact a JOIN ai_runtime_run r ON r.id=a.run_id WHERE"
                + " r.run_key=:key AND r.user_id=:user ORDER BY a.id")
        .param("key", key)
        .param("user", user)
        .query()
        .listOfRows();
  }

  public Map<String, Object> artifact(long user, String key, long artifactId) {
    return jdbc.sql(
            "SELECT a.id,a.file_name,a.content_type,a.storage_path,a.size_bytes FROM"
                + " ai_runtime_run_artifact a JOIN ai_runtime_run r ON r.id=a.run_id WHERE"
                + " r.run_key=:key AND r.user_id=:user AND a.id=:artifact")
        .param("key", key)
        .param("user", user)
        .param("artifact", artifactId)
        .query()
        .singleRow();
  }

  public Run cancel(long user, String key) {
    Map<String, Object> row =
        jdbc.sql(
                "SELECT runtime_type,status FROM ai_runtime_run WHERE run_key=:key AND"
                    + " user_id=:user")
            .param("key", key)
            .param("user", user)
            .query()
            .singleRow();
    String status = String.valueOf(row.get("status"));
    if (Set.of("COMPLETED", "FAILED", "CANCELLED").contains(status)) return view(user, key);
    jdbc.sql(
            "UPDATE ai_runtime_run SET status='CANCELLED',error_code='USER_CANCELLED' WHERE"
                + " run_key=:key AND user_id=:user")
        .param("key", key)
        .param("user", user)
        .update();
    if ("AGENT".equals(String.valueOf(row.get("runtime_type")))) {
      try {
        String url =
            jdbc.sql(
                    "SELECT gateway_url FROM ai_agent_definition WHERE status='ACTIVE' ORDER BY id"
                        + " LIMIT 1")
                .query(String.class)
                .optional()
                .orElse(fallbackUrl);
        HttpRequest req =
            HttpRequest.newBuilder(URI.create(url + "/agent-runs/" + key))
                .timeout(Duration.ofSeconds(3))
                .DELETE()
                .build();
        HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding());
      } catch (Exception ignored) {
      }
    }
    return view(user, key);
  }

  private void seedParticipants(String key, List<String> providers) {
    List<String> values =
        providers == null || providers.isEmpty() ? List.of("KIMI", "DEEPSEEK", "QWEN") : providers;
    long id =
        jdbc.sql("SELECT id FROM ai_runtime_run WHERE run_key=:key")
            .param("key", key)
            .query(Long.class)
            .single();
    for (int i = 0; i < values.size(); i++)
      jdbc.sql(
              "INSERT INTO ai_web_agent_participant(run_id,provider_key,role_key,status)"
                  + " VALUES(:run,:provider,:role,'WAITING_EXTENSION')")
          .param("run", id)
          .param("provider", values.get(i))
          .param("role", i == 0 ? "RESEARCHER" : i == 1 ? "ANALYST" : "REVIEWER")
          .update();
  }

  public Run view(long user, String key) {
    Map<String, Object> row =
        jdbc.sql(
                "SELECT"
                    + " run_key,runtime_type,goal,status,response_payload,error_code,created_at,updated_at"
                    + " FROM ai_runtime_run WHERE run_key=:key AND user_id=:user")
            .param("key", key)
            .param("user", user)
            .query()
            .singleRow();
    List<Map<String, Object>> parts =
        jdbc.sql(
                "SELECT p.provider_key,p.role_key,p.status,p.result_text,p.error_code FROM"
                    + " ai_web_agent_participant p JOIN ai_runtime_run r ON r.id=p.run_id WHERE"
                    + " r.run_key=:key ORDER BY p.id")
            .param("key", key)
            .query()
            .listOfRows();
    return new Run(row, parts);
  }

  public Run report(long user, String key, Report r) {
    long run =
        jdbc.sql(
                "SELECT id FROM ai_runtime_run WHERE run_key=:key AND user_id=:user AND"
                    + " runtime_type='MULTI_WEB_AGENT'")
            .param("key", key)
            .param("user", user)
            .query(Long.class)
            .single();
    if ("SYNTHESIS".equals(r.phase())) {
      if ("COMPLETED".equals(r.status()))
        jdbc.sql(
                "UPDATE ai_runtime_run SET"
                    + " status='WAITING_AGENT_REVIEW',response_payload=CAST(:response AS"
                    + " JSON),error_code=NULL WHERE id=:run")
            .param(
                "response",
                write(
                    Map.of("summary", r.result() == null ? "" : r.result(), "phase", "SYNTHESIS")))
            .param("run", run)
            .update();
      else if ("RUNNING".equals(r.status()))
        jdbc.sql("UPDATE ai_runtime_run SET status='SYNTHESIZING',error_code=NULL WHERE id=:run")
            .param("run", run)
            .update();
      else
        jdbc.sql("UPDATE ai_runtime_run SET status='FAILED',error_code=:error WHERE id=:run")
            .param("error", r.errorCode() == null ? "SYNTHESIS_FAILED" : r.errorCode())
            .param("run", run)
            .update();
      return view(user, key);
    }
    jdbc.sql(
            "UPDATE ai_web_agent_participant SET"
                + " status=:status,result_text=:result,error_code=:error,completed_at=CASE WHEN"
                + " :status IN ('COMPLETED','FAILED') THEN CURRENT_TIMESTAMP(6) ELSE NULL END WHERE"
                + " run_id=:run AND provider_key=:provider")
        .param("status", r.status())
        .param("result", r.result())
        .param("error", r.errorCode())
        .param("run", run)
        .param("provider", r.provider())
        .update();
    long pending =
        jdbc.sql(
                "SELECT COUNT(*) FROM ai_web_agent_participant WHERE run_id=:run AND status NOT IN"
                    + " ('COMPLETED','FAILED','LOGIN_REQUIRED')")
            .param("run", run)
            .query(Long.class)
            .single();
    long login =
        jdbc.sql(
                "SELECT COUNT(*) FROM ai_web_agent_participant WHERE run_id=:run AND"
                    + " status='LOGIN_REQUIRED'")
            .param("run", run)
            .query(Long.class)
            .single();
    if (login > 0)
      jdbc.sql("UPDATE ai_runtime_run SET status='WAITING_LOGIN' WHERE id=:run")
          .param("run", run)
          .update();
    else if (pending == 0)
      jdbc.sql("UPDATE ai_runtime_run SET status='SYNTHESIZING',error_code=NULL WHERE id=:run")
          .param("run", run)
          .update();
    else
      jdbc.sql("UPDATE ai_runtime_run SET status='RUNNING',error_code=NULL WHERE id=:run")
          .param("run", run)
          .update();
    return view(user, key);
  }

  private String write(Object v) {
    try {
      return json.writeValueAsString(v);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String blank(String v) {
    return v == null ? "" : v;
  }

  public record Start(
      String runtimeType,
      String goal,
      String credentialSource,
      String provider,
      String model,
      String ephemeralApiKey,
      Boolean allowWebSearch,
      List<String> providers) {}

  public record Report(
      String provider, String status, String result, String errorCode, String phase) {}

  public record Run(Map<String, Object> run, List<Map<String, Object>> participants) {}
}
