package cn.finalscompass.config;

import cn.finalscompass.service.AuthService;
import cn.finalscompass.service.SystemModuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.servlet.HandlerInterceptor;

/** 在后端阻止维护模块的业务调用，避免仅靠前端维护页被直接请求绕过 */
public final class ModuleMaintenanceInterceptor implements HandlerInterceptor {
  private final AuthService auth;private final SystemModuleService modules;private final ObjectMapper json;
  public ModuleMaintenanceInterceptor(AuthService auth,SystemModuleService modules,ObjectMapper json){this.auth=auth;this.modules=modules;this.json=json;}
  @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws Exception{
    String key=module(request.getRequestURI());if(key==null)return true;
    try{if("ADMIN".equals(auth.current(request).role()))return true;}catch(Exception ignored){}
    var setting=modules.require(key);if(!"MAINTENANCE".equals(setting.status()))return true;
    response.setStatus(503);response.setContentType("application/json;charset=UTF-8");response.setHeader("Retry-After","1800");Map<String,Object> body=new LinkedHashMap<>();body.put("code","MODULE_MAINTENANCE");body.put("module",key);body.put("title",setting.maintenanceTitle());body.put("message",setting.maintenanceContent());body.put("estimatedRecoveryAt",setting.estimatedRecoveryAt());response.getWriter().write(json.writeValueAsString(body));return false;
  }
  private String module(String uri){if(uri.startsWith("/api/ai-center/")||uri.startsWith("/api/ai/vision/")||uri.startsWith("/api/ai/attachments/"))return "AI_CENTER";if(uri.startsWith("/api/cet/"))return "CET_PRACTICE";if(uri.startsWith("/api/courses/")||uri.startsWith("/api/circles/"))return "COURSE_NAVIGATION";return null;}
}
