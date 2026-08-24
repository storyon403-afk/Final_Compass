package cn.finalscompass.service;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 集中管理业务模块开放状态；前端展示与后端拦截必须读取同一份配置 */
@Service
public class SystemModuleService {
  private final JdbcClient jdbc;
  public SystemModuleService(JdbcClient jdbc){this.jdbc=jdbc;}

  public List<ModuleSetting> all(){return jdbc.sql("SELECT module_key,status,maintenance_title,maintenance_content,estimated_recovery_at,updated_at FROM system_module_setting ORDER BY module_key").query(ModuleSetting.class).list();}
  public ModuleSetting require(String key){return jdbc.sql("SELECT module_key,status,maintenance_title,maintenance_content,estimated_recovery_at,updated_at FROM system_module_setting WHERE module_key=:key").param("key",key).query(ModuleSetting.class).single();}
  public boolean maintenance(String key){return "MAINTENANCE".equals(require(key).status());}
  @Transactional public ModuleSetting update(long admin, String key, Update input){
    if(!List.of("COURSE_NAVIGATION","AI_CENTER","CET_PRACTICE").contains(key))throw new IllegalArgumentException("模块标识不合法");
    if(input.title()==null||input.title().isBlank()||input.content()==null||input.content().isBlank())throw new IllegalArgumentException("维护标题和说明不能为空");
    jdbc.sql("UPDATE system_module_setting SET status=:status,maintenance_title=:title,maintenance_content=:content,estimated_recovery_at=:recovery,updated_by=:admin WHERE module_key=:key")
      .param("status",input.maintenance()?"MAINTENANCE":"OPEN").param("title",input.title().trim()).param("content",input.content().trim()).param("recovery",input.estimatedRecoveryAt()).param("admin",admin).param("key",key).update();
    return require(key);
  }
  public record ModuleSetting(String moduleKey,String status,String maintenanceTitle,String maintenanceContent,Instant estimatedRecoveryAt,Instant updatedAt){}
  public record Update(boolean maintenance,String title,String content,Instant estimatedRecoveryAt){}
}
