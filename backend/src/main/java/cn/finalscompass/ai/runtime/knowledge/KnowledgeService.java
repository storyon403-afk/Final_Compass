package cn.finalscompass.ai.runtime.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import cn.finalscompass.ai.runtime.provider.embedding.RuntimeEmbeddingGateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public final class KnowledgeService {
    private final JdbcClient jdbc;private final TransactionTemplate transactions;private final KnowledgeChunker chunker;private final ObjectMapper json;private final RuntimeEmbeddingGateway embeddings;
    public KnowledgeService(JdbcClient jdbc,TransactionTemplate transactions,KnowledgeChunker chunker,ObjectMapper json,RuntimeEmbeddingGateway embeddings){this.jdbc=jdbc;this.transactions=transactions;this.chunker=chunker;this.json=json;this.embeddings=embeddings;}
    public IngestResult ingestApproved(long adminId,IngestCommand command){validate(command);String sourceKey=UUID.randomUUID().toString();String markdown=normalize(command.markdown());String digest=digest(markdown);
        jdbc.sql("INSERT INTO knowledge_source(source_key,source_type,external_reference,title,scope_type,scope_key,status,submitted_by,approved_by,approved_at,content_digest) VALUES(:key,:type,:reference,:title,:scopeType,:scopeKey,'APPROVED',:admin,:admin,CURRENT_TIMESTAMP(6),:digest)")
                .param("key",sourceKey).param("type",command.sourceType()).param("reference",blank(command.externalReference()))
                .param("title",command.title().trim()).param("scopeType",command.scopeType()).param("scopeKey",command.scopeKey().trim())
                .param("admin",adminId).param("digest",digest).update();
        long sourceId=jdbc.sql("SELECT id FROM knowledge_source WHERE source_key=:key").param("key",sourceKey).query(Long.class).single();
        try{Integer count=transactions.execute(status->{jdbc.sql("UPDATE knowledge_source SET status='PROCESSING' WHERE id=:id AND status='APPROVED'").param("id",sourceId).update();
            jdbc.sql("INSERT INTO knowledge_document(source_id,version,markdown,metadata) VALUES(:source,1,:markdown,CAST(:metadata AS JSON))")
                    .param("source",sourceId).param("markdown",markdown).param("metadata",write(Map.of("title",command.title().trim(),"sourceType",command.sourceType()))).update();
            long documentId=jdbc.sql("SELECT id FROM knowledge_document WHERE source_id=:source AND version=1").param("source",sourceId).query(Long.class).single();
            List<KnowledgeChunker.Chunk> chunks=chunker.chunk(markdown);for(var chunk:chunks)jdbc.sql("INSERT INTO knowledge_chunk(document_id,chunk_index,heading,content,character_start,character_end,token_estimate,metadata) VALUES(:document,:idx,:heading,:content,:start,:end,:tokens,JSON_OBJECT())")
                    .param("document",documentId).param("idx",chunk.index()).param("heading",chunk.heading()).param("content",chunk.content()).param("start",chunk.start()).param("end",chunk.end()).param("tokens",chunk.tokenEstimate()).update();
            jdbc.sql("UPDATE knowledge_source SET status='READY' WHERE id=:id").param("id",sourceId).update();return chunks.size();});
            embedSource(sourceId);return new IngestResult(sourceKey,"READY",count==null?0:count,digest);
        }catch(RuntimeException failure){jdbc.sql("UPDATE knowledge_source SET status='FAILED',error_code='PIPELINE_FAILED' WHERE id=:id").param("id",sourceId).update();throw failure;}
    }
    public List<SearchResult> search(long userId,String knowledgeScope,String query,int limit){
        if(query==null||query.isBlank()||query.length()>500||limit<1||limit>20)throw new IllegalArgumentException("Knowledge query is invalid");
        String userScope="user:"+userId;RuntimeEmbeddingGateway.EmbeddingBatch queryVector=null;try{queryVector=embeddings.embed(List.of(query.trim()));}catch(RuntimeException ignored){}
        List<SearchRow> rows=jdbc.sql("""
                SELECT s.source_key,s.title,s.scope_type,s.scope_key,c.heading,c.content,c.chunk_index,
                  c.embedding_model,c.embedding_dimension,c.embedding
                FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.document_id AND d.active=TRUE
                JOIN knowledge_source s ON s.id=d.source_id AND s.status='READY'
                WHERE (s.scope_type='PUBLIC' OR (s.scope_type='USER' AND s.scope_key=:userScope)
                    OR (s.scope_type='COURSE' AND :knowledgeScope IS NOT NULL AND s.scope_key=:knowledgeScope))
                ORDER BY c.id DESC LIMIT 1000
                """).param("userScope",userScope).param("knowledgeScope",blank(knowledgeScope))
                .query((rs,row)->new SearchRow(rs.getString("source_key"),rs.getString("title"),rs.getString("scope_type"),rs.getString("scope_key"),rs.getString("heading"),rs.getString("content"),rs.getInt("chunk_index"),rs.getString("embedding_model"),nullableInt(rs,"embedding_dimension"),rs.getBytes("embedding"))).list();
        final RuntimeEmbeddingGateway.EmbeddingBatch vector=queryVector;String normalized=query.trim().toLowerCase(Locale.ROOT);
        return rows.stream().map(row->{double lexical=(row.heading()!=null&&row.heading().toLowerCase(Locale.ROOT).contains(normalized)?1:0)+(row.content().toLowerCase(Locale.ROOT).contains(normalized)?1:0);double semantic=vector==null?0:similarity(row,vector);double score=(vector==null?0:0.65*Math.max(0,semantic))+0.35*Math.min(1,lexical);return new Scored(row,score,lexical);})
                .filter(value->value.score()>0).sorted(Comparator.comparingDouble(Scored::score).reversed().thenComparing(v->v.row().sourceKey()).thenComparingInt(v->v.row().chunkIndex())).limit(limit)
                .map(value->new SearchResult(value.row().sourceKey(),value.row().title(),value.row().scopeType(),value.row().scopeKey(),value.row().heading(),value.row().content(),value.row().chunkIndex(),value.score())).toList();
    }
    private void embedSource(long sourceId){List<ChunkRow> chunks=jdbc.sql("SELECT c.id,c.content FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.document_id WHERE d.source_id=:source AND c.embedding_status IN ('PENDING','FAILED') ORDER BY c.chunk_index").param("source",sourceId).query(ChunkRow.class).list();
        for(int offset=0;offset<chunks.size();offset+=64){List<ChunkRow> batch=chunks.subList(offset,Math.min(chunks.size(),offset+64));String job=UUID.randomUUID().toString();jdbc.sql("INSERT INTO knowledge_embedding_job(job_key,source_id,provider_key,model_key,chunk_count) VALUES(:job,:source,'unresolved','unresolved',:count)").param("job",job).param("source",sourceId).param("count",batch.size()).update();try{batch.forEach(c->jdbc.sql("UPDATE knowledge_chunk SET embedding_status='PROCESSING',embedding_attempts=embedding_attempts+1 WHERE id=:id").param("id",c.id()).update());var embedded=embeddings.embed(batch.stream().map(ChunkRow::content).toList());for(int i=0;i<batch.size();i++)jdbc.sql("UPDATE knowledge_chunk SET embedding_status='READY',embedding_model=:model,embedding_dimension=:dimension,embedding=:embedding,embedding_error_code=NULL WHERE id=:id").param("model",embedded.modelKey()).param("dimension",embedded.dimension()).param("embedding",encode(embedded.vectors().get(i))).param("id",batch.get(i).id()).update();jdbc.sql("UPDATE knowledge_embedding_job SET provider_key=:provider,model_key=:model,status='SUCCEEDED',embedded_count=:count,completed_at=CURRENT_TIMESTAMP(6) WHERE job_key=:job").param("provider",embedded.providerKey()).param("model",embedded.modelKey()).param("count",batch.size()).param("job",job).update();}
            catch(RuntimeException failure){batch.forEach(c->jdbc.sql("UPDATE knowledge_chunk SET embedding_status='FAILED',embedding_error_code='EMBEDDING_FAILED' WHERE id=:id").param("id",c.id()).update());jdbc.sql("UPDATE knowledge_embedding_job SET status='FAILED',error_code='EMBEDDING_FAILED',completed_at=CURRENT_TIMESTAMP(6) WHERE job_key=:job").param("job",job).update();return;}}
    }
    private byte[] encode(float[] values){var buffer=java.nio.ByteBuffer.allocate(values.length*Float.BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(float value:values)buffer.putFloat(value);return buffer.array();}
    private float[] decode(byte[] bytes,int dimension){if(bytes==null||dimension<1||bytes.length!=dimension*Float.BYTES)return null;var buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);float[] values=new float[dimension];for(int i=0;i<dimension;i++)values[i]=buffer.getFloat();return values;}
    private double similarity(SearchRow row,RuntimeEmbeddingGateway.EmbeddingBatch query){if(row.embeddingDimension()==null||row.embeddingDimension()!=query.dimension()||!query.modelKey().equals(row.embeddingModel()))return 0;float[] value=decode(row.embedding(),row.embeddingDimension());if(value==null)return 0;float[] target=query.vectors().getFirst();double dot=0,a=0,b=0;for(int i=0;i<value.length;i++){dot+=value[i]*target[i];a+=value[i]*value[i];b+=target[i]*target[i];}return a==0||b==0?0:dot/(Math.sqrt(a)*Math.sqrt(b));}
    private Integer nullableInt(java.sql.ResultSet rs,String name)throws java.sql.SQLException{int value=rs.getInt(name);return rs.wasNull()?null:value;}
    private void validate(IngestCommand c){if(c==null||c.title()==null||c.title().isBlank()||c.title().length()>255||!Set.of("UPLOAD","TEACHER_PROFILE","FORUM","GUIDE","ADMIN").contains(c.sourceType())||!Set.of("PUBLIC","USER","COURSE").contains(c.scopeType())||c.scopeKey()==null||c.scopeKey().isBlank()||c.scopeKey().length()>120||c.markdown()==null||c.markdown().isBlank()||c.markdown().length()>2_000_000)throw new IllegalArgumentException("Knowledge ingest command is invalid");if("PUBLIC".equals(c.scopeType())&&!"public".equals(c.scopeKey()))throw new IllegalArgumentException("Public Knowledge scope is invalid");if("USER".equals(c.scopeType())&&!c.scopeKey().matches("user:[1-9][0-9]*"))throw new IllegalArgumentException("User Knowledge scope is invalid");}
    private String normalize(String value){return value.replace("\r\n","\n").replace('\r','\n').trim()+"\n";}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String digest(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record IngestCommand(String sourceType,String externalReference,String title,String scopeType,String scopeKey,String markdown){}
    public record IngestResult(String sourceKey,String status,int chunks,String contentDigest){}
    public record SearchResult(String sourceKey,String title,String scopeType,String scopeKey,String heading,String content,int chunkIndex,double score){}
    private record ChunkRow(long id,String content){}
    private record SearchRow(String sourceKey,String title,String scopeType,String scopeKey,String heading,String content,int chunkIndex,String embeddingModel,Integer embeddingDimension,byte[] embedding){}
    private record Scored(SearchRow row,double score,double lexical){}
}
