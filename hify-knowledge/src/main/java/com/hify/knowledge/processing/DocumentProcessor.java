package com.hify.knowledge.processing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.exception.BizException;
import com.hify.common.exception.LlmApiException;
import com.hify.knowledge.config.KnowledgeProperties;
import com.hify.knowledge.constant.DocumentStatus;
import com.hify.knowledge.embedding.EmbeddingClient;
import com.hify.knowledge.entity.Document;
import com.hify.knowledge.mapper.DocumentMapper;
import com.hify.knowledge.repository.DocumentChunkRepository;
import com.hify.knowledge.repository.DocumentChunkWrite;
import com.hify.knowledge.storage.DocumentFileStorage;
import com.hify.provider.dto.ModelInvocationConfig;
import com.hify.provider.service.ModelConfigService;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** Runs the complete parse, chunk, embed and persistence pipeline off request threads. */
@Component
public class DocumentProcessor {

  private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);
  private static final int MAX_ERROR_LENGTH = 500;
  private static final Set<String> SUPPORTED_EMBEDDING_PROVIDER_TYPES =
      Set.of("OPENAI", "OPENAI_COMPATIBLE");

  private final DocumentMapper documentMapper;
  private final DocumentFileStorage fileStorage;
  private final DocumentTextExtractor textExtractor;
  private final DocumentChunker chunker;
  private final EmbeddingClient embeddingClient;
  private final DocumentChunkRepository chunkRepository;
  private final ModelConfigService modelConfigService;
  private final ThreadPoolTaskExecutor llmExecutor;
  private final KnowledgeProperties properties;

  public DocumentProcessor(DocumentMapper documentMapper, DocumentFileStorage fileStorage,
      DocumentTextExtractor textExtractor, DocumentChunker chunker,
      EmbeddingClient embeddingClient, DocumentChunkRepository chunkRepository,
      ModelConfigService modelConfigService,
      @Qualifier("llmExecutor") ThreadPoolTaskExecutor llmExecutor,
      KnowledgeProperties properties) {
    this.documentMapper = documentMapper;
    this.fileStorage = fileStorage;
    this.textExtractor = textExtractor;
    this.chunker = chunker;
    this.embeddingClient = embeddingClient;
    this.chunkRepository = chunkRepository;
    this.modelConfigService = modelConfigService;
    this.llmExecutor = llmExecutor;
    this.properties = properties;
  }

  public void process(Long documentId) {
    try {
      Document document = updateStatusToProcessing(documentId);
      if (document == null) {
        return;
      }
      Path path = fileStorage.pathFor(document.getKnowledgeBaseId(), document.getId(),
          document.getFileType());
      String text = extractText(path, document.getFileType());
      List<ChunkDTO> chunks = splitChunks(text);
      chunks = embedChunks(chunks);
      saveChunks(documentId, document.getKnowledgeBaseId(), chunks);
      markDone(documentId, chunks.size());
    } catch (ProcessingCancelledException e) {
      cleanupChunks(documentId);
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      markFailed(documentId, e);
    }
  }

  private Document updateStatusToProcessing(Long documentId) {
    int claimed = documentMapper.update(null, Wrappers.<Document>lambdaUpdate()
        .eq(Document::getId, documentId)
        .eq(Document::getStatus, DocumentStatus.PENDING)
        .set(Document::getStatus, DocumentStatus.PROCESSING)
        .set(Document::getErrorMessage, "")
        .set(Document::getUpdatedAt, LocalDateTime.now()));
    if (claimed == 0) {
      return null;
    }
    Document document = documentMapper.selectById(documentId);
    if (document == null || !DocumentStatus.PROCESSING.equals(document.getStatus())) {
      throw new ProcessingCancelledException();
    }
    return document;
  }

  private String extractText(Path filePath, String fileType) {
    return textExtractor.extract(filePath, fileType);
  }

  private List<ChunkDTO> splitChunks(String text) {
    return chunker.split(text);
  }

  private List<ChunkDTO> embedChunks(List<ChunkDTO> chunks)
      throws InterruptedException, ExecutionException, TimeoutException {
    Long modelConfigId = properties.getEmbedding().getModelConfigId();
    ModelInvocationConfig invocationConfig =
        modelConfigService.requireInvocationConfig(modelConfigId);
    if (!SUPPORTED_EMBEDDING_PROVIDER_TYPES.contains(invocationConfig.getProviderType())) {
      throw new IllegalStateException("Unsupported embedding provider type");
    }
    List<String> texts = chunks.stream().map(ChunkDTO::content).toList();
    Future<List<float[]>> future = llmExecutor.submit(
        () -> embeddingClient.embed(texts, invocationConfig));
    int batchSize = Math.min(20, Math.max(1, properties.getEmbedding().getBatchSize()));
    int batches = Math.max(1, (texts.size() + batchSize - 1) / batchSize);
    long timeoutMs = Math.multiplyExact(properties.getEmbedding().getTimeout().toMillis(), batches);
    List<float[]> embeddings;
    try {
      embeddings = future.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw e;
    }
    if (embeddings.size() != chunks.size()) {
      throw new IllegalStateException("Embedding response count mismatch");
    }
    List<ChunkDTO> embedded = new ArrayList<>(chunks.size());
    for (int i = 0; i < chunks.size(); i++) {
      embedded.add(chunks.get(i).withEmbedding(embeddings.get(i)));
    }
    return List.copyOf(embedded);
  }

  private void saveChunks(Long documentId, Long knowledgeBaseId, List<ChunkDTO> chunks) {
    Document current = documentMapper.selectById(documentId);
    if (current == null || !DocumentStatus.PROCESSING.equals(current.getStatus())) {
      throw new ProcessingCancelledException();
    }

    List<DocumentChunkWrite> rows = new ArrayList<>(chunks.size());
    for (ChunkDTO chunk : chunks) {
      float[] embedding = chunk.embedding();
      if (embedding == null || embedding.length != properties.getVectorDimension()) {
        throw new IllegalStateException("Embedding vector dimension mismatch");
      }
      rows.add(new DocumentChunkWrite(knowledgeBaseId, documentId, chunk.chunkIndex(),
          chunk.content(), chunk.tokenCount(), embedding));
    }
    chunkRepository.logicalDeleteByDocumentId(documentId);
    chunkRepository.batchInsert(rows);
  }

  private void markDone(Long documentId, int chunkCount) {
    int completed = documentMapper.update(null, Wrappers.<Document>lambdaUpdate()
        .eq(Document::getId, documentId)
        .eq(Document::getStatus, DocumentStatus.PROCESSING)
        .set(Document::getStatus, DocumentStatus.DONE)
        .set(Document::getChunkCount, chunkCount)
        .set(Document::getErrorMessage, "")
        .set(Document::getUpdatedAt, LocalDateTime.now()));
    if (completed == 0) {
      cleanupChunks(documentId);
    }
  }

  private void markFailed(Long documentId, Exception error) {
    log.error("document-process failed documentId={}", documentId, error);
    cleanupChunks(documentId);
    documentMapper.update(null, Wrappers.<Document>lambdaUpdate()
        .eq(Document::getId, documentId)
        .eq(Document::getStatus, DocumentStatus.PROCESSING)
        .set(Document::getStatus, DocumentStatus.FAILED)
        .set(Document::getChunkCount, 0)
        .set(Document::getErrorMessage, safeMessage(error))
        .set(Document::getUpdatedAt, LocalDateTime.now()));
  }

  private void cleanupChunks(Long documentId) {
    try {
      chunkRepository.logicalDeleteByDocumentId(documentId);
    } catch (Exception cleanupError) {
      log.error("document-chunk-cleanup failed documentId={}", documentId, cleanupError);
    }
  }

  private String safeMessage(Exception error) {
    Throwable cause = error instanceof ExecutionException && error.getCause() != null
        ? error.getCause() : error;
    String message;
    if (cause instanceof TimeoutException) {
      message = "Embedding 调用超时";
    } else if (cause instanceof BizException) {
      message = cause.getMessage();
    } else if (cause instanceof LlmApiException llmError) {
      message = "Embedding 调用失败: " + llmError.getErrorType();
    } else {
      message = "文档处理失败，请检查文件和 Embedding 服务配置";
    }
    return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
  }

  private static final class ProcessingCancelledException extends RuntimeException {
  }
}
