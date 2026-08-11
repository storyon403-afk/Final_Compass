package cn.finalscompass.controller;

import cn.finalscompass.ai.runtime.mcp.RuntimeMcpAdminService;
import cn.finalscompass.ai.runtime.mcp.RuntimeMcpOAuthService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/mcp")
public final class RuntimeMcpAdminController {
    private final AuthService auth;
    private final RuntimeMcpAdminService admin;
    private final RuntimeMcpOAuthService oauth;
    private final String frontendOrigin;

    public RuntimeMcpAdminController(AuthService auth, RuntimeMcpAdminService admin,
                                     RuntimeMcpOAuthService oauth,
                                     @Value("${app.mail.frontend-url:http://127.0.0.1:5173}") String frontendOrigin) {
        this.auth=auth; this.admin=admin; this.oauth=oauth; this.frontendOrigin=frontendOrigin;
    }

    @GetMapping public Map<String,Object> overview(HttpServletRequest request) {
        auth.requireAdmin(request); return admin.overview();
    }
    @PostMapping("/servers/{key}/discover") public Object discover(
            HttpServletRequest request,@PathVariable String key) {
        return admin.discover(key,auth.requireAdmin(request).id());
    }
    @PutMapping("/servers") public void saveServer(HttpServletRequest request,
                                                    @RequestBody RuntimeMcpAdminService.ServerInput input){
        admin.saveServer(input,auth.requireAdmin(request).id());
    }
    @GetMapping("/servers/{key}/diff") public List<Map<String,Object>> diff(
            HttpServletRequest request,@PathVariable String key) {
        auth.requireAdmin(request); return admin.diff(key);
    }
    @PostMapping("/approvals") public void requestApproval(HttpServletRequest request,
                                                            @RequestBody ApprovalRequest input) {
        var user=auth.requireAdmin(request); admin.requestApproval(input.discoveredToolId(),input.toolKey(),
                input.version(),input.riskLevel(),input.permissions(),user.id());
    }
    @PostMapping("/approvals/{id}/decision") public void decide(HttpServletRequest request,
                                                                 @PathVariable long id,
                                                                 @RequestBody Decision input) {
        var user=auth.requireAdmin(request); admin.decide(id,input.approve(),input.note(),user.id());
    }
    @PostMapping("/servers/{key}/oauth/authorize") public Map<String,String> authorize(
            HttpServletRequest request,@PathVariable String key) {
        return Map.of("authorizationUrl",oauth.authorizationUrl(auth.requireAdmin(request).id(),key));
    }
    @DeleteMapping("/servers/{key}/oauth") public void disconnect(
            HttpServletRequest request,@PathVariable String key) {
        oauth.disconnect(key,auth.requireAdmin(request).id());
    }
    @GetMapping(value="/oauth/callback",produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(@RequestParam String code,@RequestParam String state) {
        String server=oauth.complete(code,state); String origin=frontendOrigin.replace("'","");
        return ResponseEntity.ok("<!doctype html><meta charset='utf-8'><p>MCP OAuth 已连接，可以关闭窗口。</p>"
                +"<script>if(window.opener){window.opener.postMessage({type:'fc-mcp-oauth',status:'connected',server:'"
                +server.replace("'","")+"'},'"+origin+"');window.close()}</script>");
    }
    public record ApprovalRequest(long discoveredToolId,String toolKey,String version,
                                  String riskLevel,List<String> permissions) {}
    public record Decision(boolean approve,String note) {}
}
