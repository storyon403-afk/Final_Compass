package cn.finalscompass.ai.runtime.knowledge;

import cn.finalscompass.ai.runtime.provider.embedding.RuntimeEmbeddingGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

// RAG 知识库引擎：知识入库-->切块-->向量化-->存储-->按权限检索-->语义+关键词混合排序
// 1.ingestApproved()把审核通过的资料（markdown）入库 2.search()根据用户问题，从知识库里找最相关的内容
// 维护入口：入库链路改 ingestApproved，召回与混排改 search，分块和向量协议分别改 Chunker/EmbeddingGateway。
@Service
public final class KnowledgeService {
  // 声明
  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;
  private final KnowledgeChunker chunker;
  private final ObjectMapper json;
  private final RuntimeEmbeddingGateway embeddings;

  // 注入
  public KnowledgeService(
      JdbcClient jdbc,
      TransactionTemplate transactions,
      KnowledgeChunker chunker,
      ObjectMapper json,
      RuntimeEmbeddingGateway embeddings) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.chunker = chunker;
    this.json = json;
    this.embeddings = embeddings;
  }

  /**
   * ingestApproved ingests approved knowledge sources into the system（把管理员已经批准的一份资料正式加入知识库）
   *
   * @param adminId the ID of the administrator who approved the source（哪个管理员完成了这次审核和入库）
   * @param command the ingestion command containing the source details（整份资料信息）
   * @return the result of the ingestion operation
   */
  public IngestResult ingestApproved(long adminId, IngestCommand command) {
    // 验证命令是否合法
    validate(command);
    // 给这份知识资料生成一个公开唯一标识,与业务层id不同，业务层id是自增的，公开唯一标识是UUID
    String sourceKey = UUID.randomUUID().toString();
    // 把markdown内容标准化，去掉多余空格和换行
    String markdown = normalize(command.markdown());
    // 计算 SHA-256 摘要，作为内容指纹
    String digest = digest(markdown);
    // 创建知识源
    jdbc.sql(
            """
            INSERT INTO knowledge_source(
              source_key,source_type,external_reference,title,scope_type,scope_key,status,
              submitted_by,approved_by,approved_at,content_digest
            )
            VALUES(
              :key,:type,:reference,:title,:scopeType,:scopeKey,'APPROVED',
              :admin,:admin,CURRENT_TIMESTAMP(6),:digest
            )
            """)
        .param("key", sourceKey)
        .param("type", command.sourceType())
        .param("reference", blank(command.externalReference()))
        .param("title", command.title().trim())
        .param("scopeType", command.scopeType())
        .param("scopeKey", command.scopeKey().trim())
        .param("admin", adminId)
        .param("digest", digest)
        .update();
    // sourceKey是公开唯一标识，sourceId是数据库自增主键id，查询数据库获取sourceId
    long sourceId =
        jdbc.sql("SELECT id FROM knowledge_source WHERE source_key=:key")
            .param("key", sourceKey)
            .query(Long.class)
            .single();
    try {
      // 进入事物，把知识源状态改为PROCESSING，插入知识文档，切块，插入知识块，最后把知识源状态改为READY
      Integer count =
          transactions.execute(
              status -> {
                jdbc.sql(
                        "UPDATE knowledge_source SET status='PROCESSING' WHERE id=:id AND"
                            + " status='APPROVED'")
                    .param("id", sourceId)
                    .update();
                // 插入知识文档，预留版本管理
                jdbc.sql(
                        "INSERT INTO knowledge_document(source_id,version,markdown,metadata)"
                            + " VALUES(:source,1,:markdown,CAST(:metadata AS JSON))")
                    .param("source", sourceId)
                    .param("markdown", markdown)
                    .param(
                        "metadata",
                        write(
                            Map.of(
                                "title",
                                command.title().trim(),
                                "sourceType",
                                command.sourceType())))
                    .update();
                long documentId =
                    jdbc.sql(
                            "SELECT id FROM knowledge_document WHERE source_id=:source AND"
                                + " version=1")
                        .param("source", sourceId)
                        .query(Long.class)
                        .single();
                // 切块
                List<KnowledgeChunker.Chunk> chunks = chunker.chunk(markdown);
                // 插入知识块，character_start 和 character_end：记录这一块在原文的哪个字符区间，token_estimate：估算这一块的 token
                // 数量，metadata：预留字段
                for (var chunk : chunks)
                  jdbc.sql(
                          "INSERT INTO"
                              + " knowledge_chunk(document_id,chunk_index,heading,content,character_start,character_end,token_estimate,metadata)"
                              + " VALUES(:document,:idx,:heading,:content,:start,:end,:tokens,JSON_OBJECT())")
                      .param("document", documentId)
                      .param("idx", chunk.index())
                      .param("heading", chunk.heading())
                      .param("content", chunk.content())
                      .param("start", chunk.start())
                      .param("end", chunk.end())
                      .param("tokens", chunk.tokenEstimate())
                      .update();
                // 把知识源状态改为READY
                jdbc.sql("UPDATE knowledge_source SET status='READY' WHERE id=:id")
                    .param("id", sourceId)
                    .update();
                return chunks.size();
              });

      /*
       * transaction
       *      ↓
       * source status = READY
       *      ↓
       * transaction 提交
       *
       * embedSource(sourceId)
       */
      // 文本知识已经 READY，即使 Embedding 失败，系统仍然有可能进行 lexical 搜索
      embedSource(sourceId);
      // 返回入库结果
      return new IngestResult(sourceKey, "READY", count == null ? 0 : count, digest);
    } catch (RuntimeException failure) {
      jdbc.sql(
              "UPDATE knowledge_source SET status='FAILED',error_code='PIPELINE_FAILED' WHERE"
                  + " id=:id")
          .param("id", sourceId)
          .update();
      throw failure;
    }
  }

  // 给一个问题，从知识库中找最相关的若干 Chunk，RAG 的 Retrieval 阶段
  /**
   * @param userId 用户 ID:USER 私有知识隔离
   * @param knowledgeScope 知识范围:访问权限控制，PUBLIC/USER/COURSE
   * @param query 查询关键词
   * @param limit 返回结果数量限制
   * @return 相关的搜索结果列表
   */
  public List<SearchResult> search(long userId, String knowledgeScope, String query, int limit) {
    // 参数校验
    if (query == null || query.isBlank() || query.length() > 500 || limit < 1 || limit > 20)
      throw new IllegalArgumentException("Knowledge query is invalid");
    // 构造用户私有知识范围标识：数据库层权限隔离
    String userScope = "user:" + userId;
    // 语义搜索失败时自动降级为 lexical search
    RuntimeEmbeddingGateway.EmbeddingBatch queryVector = null;
    try {
      queryVector = embeddings.embed(userId, List.of(query.trim()));
    } catch (RuntimeException ignored) {
    }
    // 不是让数据库做向量近邻检索，先取最多 1000 个 Chunk，待优化升级;SQL 查询结果被包装为：private record SearchRow(...)
    List<SearchRow> rows =
        jdbc.sql(
                """
SELECT s.source_key,s.title,s.scope_type,s.scope_key,c.heading,c.content,c.chunk_index,
  c.embedding_model,c.embedding_dimension,c.embedding
FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.document_id AND d.active=TRUE
JOIN knowledge_source s ON s.id=d.source_id AND s.status='READY'
WHERE (s.scope_type='PUBLIC' OR (s.scope_type='USER' AND s.scope_key=:userScope)
    OR (s.scope_type='COURSE' AND :knowledgeScope IS NOT NULL AND s.scope_key=:knowledgeScope))
ORDER BY c.id DESC LIMIT 1000
""")
            .param("userScope", userScope)
            .param("knowledgeScope", blank(knowledgeScope))
            .query(
                (rs, row) ->
                    new SearchRow(
                        rs.getString("source_key"),
                        rs.getString("title"),
                        rs.getString("scope_type"),
                        rs.getString("scope_key"),
                        rs.getString("heading"),
                        rs.getString("content"),
                        rs.getInt("chunk_index"),
                        rs.getString("embedding_model"),
                        nullableInt(rs, "embedding_dimension"),
                        rs.getBytes("embedding")))
            .list();
    final RuntimeEmbeddingGateway.EmbeddingBatch vector = queryVector;
    String normalized = query.trim().toLowerCase(Locale.ROOT);
    // 对每个 Chunk 计算 lexical score 和 semantic score，计算混合评分（65% 语义相似度，35% 精确关键词命中，用 Math.max(0,
    // semantic)：不让负数扣分，范围为[0,1]）
    // 如果：vector == null，说明语义搜索失败，semantic score 为 0，score = 0.35 * lexical:只用 lexical score
    // ,所以只有：精确字符串包含 query 的 Chunk 才能被找到。
    // .sorted() 按照 score 降序排列，score 相同的按 sourceKey 升序排列，sourceKey 相同的按 chunkIndex 升序排列,最后取前 limit
    // 个结果
    return rows.stream()
        .map(
            row -> {
              double lexical =
                  (row.heading() != null
                              && row.heading().toLowerCase(Locale.ROOT).contains(normalized)
                          ? 1
                          : 0)
                      + (row.content().toLowerCase(Locale.ROOT).contains(normalized) ? 1 : 0);
              double semantic = vector == null ? 0 : similarity(row, vector);
              double score =
                  (vector == null ? 0 : 0.65 * Math.max(0, semantic)) + 0.35 * Math.min(1, lexical);
              return new Scored(row, score, lexical);
            })
        .filter(value -> value.score() > 0)
        .sorted(
            Comparator.comparingDouble(Scored::score)
                .reversed()
                .thenComparing(v -> v.row().sourceKey())
                .thenComparingInt(v -> v.row().chunkIndex()))
        .limit(limit)
        .map(
            value ->
                new SearchResult(
                    value.row().sourceKey(),
                    value.row().title(),
                    value.row().scopeType(),
                    value.row().scopeKey(),
                    value.row().heading(),
                    value.row().content(),
                    value.row().chunkIndex(),
                    value.score()))
        .toList();
  }

  // 把某个知识源下所有还没有成功向量化的 Chunk 做 Embedding
  private void embedSource(long sourceId) {
    List<ChunkRow> chunks =
        jdbc.sql(
                "SELECT c.id,c.content FROM knowledge_chunk c JOIN knowledge_document d ON"
                    + " d.id=c.document_id WHERE d.source_id=:source AND c.embedding_status IN"
                    + " ('PENDING','FAILED') ORDER BY c.chunk_index")
            .param("source", sourceId)
            .query(ChunkRow.class)
            .list();
    // 一次输入多个字符串，每次最多 64 个，分批处理，比一条一条请求高效很多；可观测性设计：knowledge_embedding_job 不仅保存最终结果，还记录每一次 Embedding
    // 批处理任务
    for (int offset = 0; offset < chunks.size(); offset += 64) {
      List<ChunkRow> batch = chunks.subList(offset, Math.min(chunks.size(), offset + 64));
      String job = UUID.randomUUID().toString();
      jdbc.sql(
              "INSERT INTO"
                  + " knowledge_embedding_job(job_key,source_id,provider_key,model_key,chunk_count)"
                  + " VALUES(:job,:source,'unresolved','unresolved',:count)")
          .param("job", job)
          .param("source", sourceId)
          .param("count", batch.size())
          .update();
      try {
        // Chunk 状态变 PROCESSING，可优化升级以后，支持多线程并发 Embedding，避免重复 Embedding
        batch.forEach(
            c ->
                jdbc.sql(
                        "UPDATE knowledge_chunk SET"
                            + " embedding_status='PROCESSING',embedding_attempts=embedding_attempts+1"
                            + " WHERE id=:id")
                    .param("id", c.id())
                    .update());
        var embedded = embeddings.embed(batch.stream().map(ChunkRow::content).toList());
        for (int i = 0; i < batch.size(); i++)
          jdbc.sql(
                  "UPDATE knowledge_chunk SET"
                      + " embedding_status='READY',embedding_model=:model,embedding_dimension=:dimension,embedding=:embedding,embedding_error_code=NULL"
                      + " WHERE id=:id")
              .param("model", embedded.modelKey())
              .param("dimension", embedded.dimension())
              .param("embedding", encode(embedded.vectors().get(i)))
              .param("id", batch.get(i).id())
              .update();
        jdbc.sql(
                "UPDATE knowledge_embedding_job SET"
                    + " provider_key=:provider,model_key=:model,status='SUCCEEDED',embedded_count=:count,completed_at=CURRENT_TIMESTAMP(6)"
                    + " WHERE job_key=:job")
            .param("provider", embedded.providerKey())
            .param("model", embedded.modelKey())
            .param("count", batch.size())
            .param("job", job)
            .update();
      } catch (RuntimeException failure) {
        batch.forEach(
            c ->
                jdbc.sql(
                        "UPDATE knowledge_chunk SET"
                            + " embedding_status='FAILED',embedding_error_code='EMBEDDING_FAILED'"
                            + " WHERE id=:id")
                    .param("id", c.id())
                    .update());
        jdbc.sql(
                "UPDATE knowledge_embedding_job SET"
                    + " status='FAILED',error_code='EMBEDDING_FAILED',completed_at=CURRENT_TIMESTAMP(6)"
                    + " WHERE job_key=:job")
            .param("job", job)
            .update();
        return;
      }
    }
  }

  // 把 float 向量按小端序编码为 byte[]，便于存入数据库的二进制字段
  private byte[] encode(float[] values) {
    var buffer =
        java.nio.ByteBuffer.allocate(values.length * Float.BYTES)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    for (float value : values) buffer.putFloat(value);
    return buffer.array();
  }

  // 把数据库中的二进制向量还原为 float[]；数据为空、维度非法或字节长度不匹配时返回 null
  private float[] decode(byte[] bytes, int dimension) {
    if (bytes == null || dimension < 1 || bytes.length != dimension * Float.BYTES) return null;
    var buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    float[] values = new float[dimension];
    for (int i = 0; i < dimension; i++) values[i] = buffer.getFloat();
    return values;
  }

  // 计算 Chunk 向量和问题向量的余弦相似度；模型、维度或向量数据不匹配时不参与语义评分
  private double similarity(SearchRow row, RuntimeEmbeddingGateway.EmbeddingBatch query) {
    if (row.embeddingDimension() == null
        || row.embeddingDimension() != query.dimension()
        || !query.modelKey().equals(row.embeddingModel())) return 0;
    float[] value = decode(row.embedding(), row.embeddingDimension());
    if (value == null) return 0;
    float[] target = query.vectors().getFirst();
    double dot = 0, a = 0, b = 0;
    for (int i = 0; i < value.length; i++) {
      dot += value[i] * target[i];
      a += value[i] * value[i];
      b += target[i] * target[i];
    }
    return a == 0 || b == 0 ? 0 : dot / (Math.sqrt(a) * Math.sqrt(b));
  }

  // 读取数据库中允许为 NULL 的整数字段，避免 SQL NULL 被 ResultSet.getInt() 当成 0
  private Integer nullableInt(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
    int value = rs.getInt(name);
    return rs.wasNull() ? null : value;
  }

  // 校验知识入库参数：限制资料类型、可见范围、范围标识以及标题和正文长度，并检查不同 scopeType 对应的 scopeKey 格式
  private void validate(IngestCommand c) {
    if (c == null
        || c.title() == null
        || c.title().isBlank()
        || c.title().length() > 255
        || !Set.of("UPLOAD", "TEACHER_PROFILE", "FORUM", "GUIDE", "ADMIN").contains(c.sourceType())
        || !Set.of("PUBLIC", "USER", "COURSE").contains(c.scopeType())
        || c.scopeKey() == null
        || c.scopeKey().isBlank()
        || c.scopeKey().length() > 120
        || c.markdown() == null
        || c.markdown().isBlank()
        || c.markdown().length() > 2_000_000)
      throw new IllegalArgumentException("Knowledge ingest command is invalid");
    if ("PUBLIC".equals(c.scopeType()) && !"public".equals(c.scopeKey()))
      throw new IllegalArgumentException("Public Knowledge scope is invalid");
    if ("USER".equals(c.scopeType()) && !c.scopeKey().matches("user:[1-9][0-9]*"))
      throw new IllegalArgumentException("User Knowledge scope is invalid");
  }

  // 统一 Markdown 换行符，去掉首尾空白，并保证正文以一个换行结尾，便于生成稳定的内容摘要和切块
  private String normalize(String value) {
    return value.replace("\r\n", "\n").replace('\r', '\n').trim() + "\n";
  }

  // 把空字符串转换为 null，非空字符串去掉首尾空白后返回，便于写入可选数据库字段
  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  // 把对象序列化为 JSON；序列化失败时统一转换为系统内部异常
  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // 计算标准化正文的 SHA-256 摘要，作为判断资料内容是否一致的指纹
  private String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // 知识入库请求：资料来源、外部引用、标题、可见范围以及 Markdown 正文
  public record IngestCommand(
      String sourceType,
      String externalReference,
      String title,
      String scopeType,
      String scopeKey,
      String markdown) {}

  // 知识入库结果：公开标识、最终状态、切块数量以及内容摘要
  public record IngestResult(String sourceKey, String status, int chunks, String contentDigest) {}

  // 对外返回的知识检索结果：知识源信息、命中的 Chunk 内容、位置以及混合评分
  public record SearchResult(
      String sourceKey,
      String title,
      String scopeType,
      String scopeKey,
      String heading,
      String content,
      int chunkIndex,
      double score) {}

  // Embedding 时使用的精简 Chunk 数据，只需要数据库 ID 和正文
  private record ChunkRow(long id, String content) {}

  // 数据库检索出的候选 Chunk，包含权限信息、正文信息以及已存储的向量数据
  private record SearchRow(
      String sourceKey,
      String title,
      String scopeType,
      String scopeKey,
      String heading,
      String content,
      int chunkIndex,
      String embeddingModel,
      Integer embeddingDimension,
      byte[] embedding) {}

  // 候选 Chunk 的临时评分结果，用于过滤、排序并转换为最终搜索结果
  private record Scored(SearchRow row, double score, double lexical) {}
}
