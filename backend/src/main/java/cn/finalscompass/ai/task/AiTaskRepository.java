        package cn.finalscompass.ai.task;

        import org.springframework.jdbc.core.simple.JdbcClient;
        import org.springframework.stereotype.Repository;

        import java.util.List;
        import java.util.Map;
        import java.util.Optional;

        @Repository
        public class AiTaskRepository {
        private final JdbcClient jdbc;

        public AiTaskRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

        public long create(long userId, String skillId, String provider, String traceId, String input) {
                jdbc.sql("""
                        INSERT INTO ai_task(user_id,skill_id,provider,status,trace_id,input_text)
                        VALUES (:user,:skill,:provider,'CREATED',:trace,:input)
                        """).param("user", userId).param("skill", skillId).param("provider", provider)
                        .param("trace", traceId).param("input", input).update();
                return jdbc.sql("SELECT id FROM ai_task WHERE trace_id=:trace").param("trace", traceId)
                        .query(Long.class).single();
        }

        public void transition(long taskId, AiTaskStatus status, int currentStep) {
                jdbc.sql("UPDATE ai_task SET status=:status,current_step=:step WHERE id=:id")
                        .param("status", status.name()).param("step", currentStep).param("id", taskId).update();
        }

        public long startStep(long taskId, int index, String type) {
                jdbc.sql("""
                        INSERT INTO ai_task_step(task_id,step_index,step_type,status)
                        VALUES (:task,:idx,:type,'EXECUTING')
                        """).param("task", taskId).param("idx", index).param("type", type).update();
                return jdbc.sql("SELECT id FROM ai_task_step WHERE task_id=:task AND step_index=:idx")
                        .param("task", taskId).param("idx", index).query(Long.class).single();
        }

        public void completeStep(long stepId, String result) {
                jdbc.sql("UPDATE ai_task_step SET status='COMPLETED',result_text=:result,completed_at=NOW() WHERE id=:id")
                        .param("result", result).param("id", stepId).update();
        }

        public void complete(long taskId, String result) {
                jdbc.sql("UPDATE ai_task SET status='COMPLETED',result_text=:result,completed_at=NOW() WHERE id=:id")
                        .param("result", result).param("id", taskId).update();
        }

        public void fail(long taskId, Long stepId, RuntimeException error) {
                String message = safeMessage(error);
                if (stepId != null) jdbc.sql("UPDATE ai_task_step SET status='FAILED',error_message=:error,completed_at=NOW() WHERE id=:id")
                        .param("error", message).param("id", stepId).update();
                jdbc.sql("UPDATE ai_task SET status='FAILED',error_message=:error,completed_at=NOW() WHERE id=:id")
                        .param("error", message).param("id", taskId).update();
        }

        public Optional<Map<String,Object>> find(long userId, long taskId) {
                return jdbc.sql("""
                        SELECT id,skill_id,provider,status,current_step,trace_id,result_text,error_message,
                        created_at,updated_at,completed_at
                        FROM ai_task WHERE id=:id AND user_id=:user
                        """).param("id", taskId).param("user", userId).query().listOfRows().stream().findFirst();
        }

        public List<Map<String,Object>> steps(long userId, long taskId) {
                return jdbc.sql("""
                        SELECT s.id,s.step_index,s.step_type,s.status,s.result_text,s.error_message,s.started_at,s.completed_at
                        FROM ai_task_step s JOIN ai_task t ON t.id=s.task_id
                        WHERE s.task_id=:task AND t.user_id=:user ORDER BY s.step_index
                        """).param("task", taskId).param("user", userId).query().listOfRows();
        }

        private String safeMessage(Throwable error) {
                String value = error.getMessage();
                if (value == null || value.isBlank()) value = error.getClass().getSimpleName();
                return value.substring(0, Math.min(500, value.length()));
        }
        }
