package com.hify.knowledge.repository;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.handler.VectorTypeHandler;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Direct SQL access to document chunks in the dedicated pgvector database. */
@Repository
public class DocumentChunkRepository {

  private static final int MAX_TOP_K = 100;

  private static final String INSERT_SQL = """
      INSERT INTO document_chunk (
        knowledge_base_id, document_id, chunk_index, content, token_count, embedding
      ) VALUES (?, ?, ?, ?, ?, CAST(? AS vector))
      """;

  private final JdbcTemplate jdbcTemplate;
  private final int vectorDimension;

  public DocumentChunkRepository(
      @Qualifier("pgvectorJdbcTemplate") JdbcTemplate jdbcTemplate,
      @Value("${hify.knowledge.vector-dimension}") int vectorDimension) {
    this.jdbcTemplate = jdbcTemplate;
    this.vectorDimension = vectorDimension;
  }

  /** Writes all chunks atomically within the PostgreSQL transaction manager. */
  @Transactional(transactionManager = "pgvectorTransactionManager")
  public void batchInsert(List<DocumentChunkWrite> chunks) {
    if (chunks == null) {
      throw parameterError("文档分块列表不能为空");
    }
    if (chunks.isEmpty()) {
      return;
    }

    Set<String> indexes = new HashSet<>();
    for (DocumentChunkWrite chunk : chunks) {
      validateChunk(chunk, indexes);
    }

    jdbcTemplate.batchUpdate(INSERT_SQL, chunks, chunks.size(), (statement, chunk) -> {
      statement.setLong(1, chunk.knowledgeBaseId());
      statement.setLong(2, chunk.documentId());
      statement.setInt(3, chunk.chunkIndex());
      statement.setString(4, chunk.content());
      statement.setInt(5, chunk.tokenCount());
      statement.setString(6, VectorTypeHandler.toLiteral(chunk.embedding()));
    });
  }

  /** Logically deletes all active chunks belonging to one knowledge base. */
  @Transactional(transactionManager = "pgvectorTransactionManager")
  public int logicalDeleteByKnowledgeBaseId(Long knowledgeBaseId) {
    if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
      throw parameterError("知识库 ID 必须大于 0");
    }
    return jdbcTemplate.update("""
        UPDATE document_chunk
        SET deleted = 1
        WHERE knowledge_base_id = ?
          AND deleted = 0
        """, knowledgeBaseId);
  }

  /** Returns the most similar chunks across the selected knowledge bases. */
  public List<DocumentChunkMatch> searchSimilar(List<Long> knowledgeBaseIds,
      float[] queryEmbedding, int topK, double minSimilarity) {
    validateSearch(knowledgeBaseIds, queryEmbedding, topK, minSimilarity);

    StringJoiner placeholders = new StringJoiner(", ");
    knowledgeBaseIds.forEach(id -> placeholders.add("?"));
    String sql = """
        WITH query_vector AS (
          SELECT CAST(? AS vector) AS embedding
        )
        SELECT
          dc.id,
          dc.knowledge_base_id,
          dc.document_id,
          dc.chunk_index,
          dc.content,
          dc.token_count,
          1 - (dc.embedding <=> q.embedding) AS similarity
        FROM document_chunk dc
        CROSS JOIN query_vector q
        WHERE dc.knowledge_base_id IN (%s)
          AND dc.deleted = 0
          AND 1 - (dc.embedding <=> q.embedding) >= ?
        ORDER BY dc.embedding <=> q.embedding
        LIMIT ?
        """.formatted(placeholders);

    Object[] args = new Object[knowledgeBaseIds.size() + 3];
    args[0] = VectorTypeHandler.toLiteral(queryEmbedding);
    for (int i = 0; i < knowledgeBaseIds.size(); i++) {
      args[i + 1] = knowledgeBaseIds.get(i);
    }
    args[args.length - 2] = minSimilarity;
    args[args.length - 1] = topK;

    return jdbcTemplate.query(sql, (resultSet, rowNum) -> new DocumentChunkMatch(
        resultSet.getLong("id"),
        resultSet.getLong("knowledge_base_id"),
        resultSet.getLong("document_id"),
        resultSet.getInt("chunk_index"),
        resultSet.getString("content"),
        resultSet.getInt("token_count"),
        resultSet.getDouble("similarity")), args);
  }

  private void validateChunk(DocumentChunkWrite chunk, Set<String> indexes) {
    if (chunk == null) {
      throw parameterError("文档分块不能为空");
    }
    if (chunk.knowledgeBaseId() <= 0 || chunk.documentId() <= 0) {
      throw parameterError("知识库 ID 和文档 ID 必须大于 0");
    }
    if (chunk.chunkIndex() < 0) {
      throw parameterError("分块序号不能小于 0");
    }
    if (chunk.content() == null || chunk.content().isBlank()) {
      throw parameterError("分块内容不能为空");
    }
    if (chunk.tokenCount() < 0) {
      throw parameterError("分块 token 数不能小于 0");
    }
    validateVector(chunk.embedding());
    if (!indexes.add(chunk.documentId() + ":" + chunk.chunkIndex())) {
      throw parameterError("同一批次存在重复的文档分块序号");
    }
  }

  private void validateSearch(List<Long> knowledgeBaseIds, float[] queryEmbedding,
      int topK, double minSimilarity) {
    if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()
        || knowledgeBaseIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw parameterError("知识库 ID 列表不能为空且 ID 必须大于 0");
    }
    if (topK < 1 || topK > MAX_TOP_K) {
      throw parameterError("topK 必须在 1 到 " + MAX_TOP_K + " 之间");
    }
    if (!Double.isFinite(minSimilarity) || minSimilarity < -1 || minSimilarity > 1) {
      throw parameterError("相似度阈值必须在 -1 到 1 之间");
    }
    validateVector(queryEmbedding);
  }

  private void validateVector(float[] vector) {
    if (vector == null || vector.length != vectorDimension) {
      throw parameterError("向量维度必须为 " + vectorDimension);
    }
    for (float value : vector) {
      if (!Float.isFinite(value)) {
        throw parameterError("向量不能包含 NaN 或无穷值");
      }
    }
  }

  private BizException parameterError(String message) {
    return new BizException(ErrorCode.PARAM_ERROR, message);
  }
}
