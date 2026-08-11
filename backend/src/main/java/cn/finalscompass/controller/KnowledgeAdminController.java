package cn.finalscompass.controller;

import cn.finalscompass.ai.runtime.knowledge.KnowledgeService;
import cn.finalscompass.service.AiDocumentConversionService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/system/knowledge")
public final class KnowledgeAdminController {
    private final AuthService auth;private final KnowledgeService knowledge;private final AiDocumentConversionService documents;
    public KnowledgeAdminController(AuthService auth,KnowledgeService knowledge,AiDocumentConversionService documents){this.auth=auth;this.knowledge=knowledge;this.documents=documents;}
    @PostMapping("/ingest-markdown") public KnowledgeService.IngestResult ingest(HttpServletRequest request,@RequestBody KnowledgeService.IngestCommand command){return knowledge.ingestApproved(auth.requireAdmin(request).id(),command);}
    @PostMapping(value="/ingest-file",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeService.IngestResult ingestFile(HttpServletRequest request,@RequestPart MultipartFile file,
            @RequestParam String sourceType,@RequestParam String title,@RequestParam String scopeType,
            @RequestParam String scopeKey,@RequestParam(required=false) String externalReference){
        long admin=auth.requireAdmin(request).id();var converted=documents.convert(file);
        return knowledge.ingestApproved(admin,new KnowledgeService.IngestCommand(sourceType,externalReference,title,scopeType,scopeKey,converted.markdown()));
    }
    @GetMapping("/search") public List<KnowledgeService.SearchResult> search(HttpServletRequest request,
            @RequestParam String query,@RequestParam(required=false) String scope,@RequestParam(defaultValue="10") int limit){
        var admin=auth.requireAdmin(request);return knowledge.search(admin.id(),scope,query,limit);
    }
}
