package com.hify.knowledge.processing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.knowledge.config.KnowledgeProperties;
import com.hify.knowledge.constant.DocumentStatus;
import com.hify.knowledge.entity.Document;
import com.hify.knowledge.mapper.DocumentMapper;
import com.hify.knowledge.storage.DocumentFileStorage;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Best-effort startup recovery for a single application instance. */
@Component
public class DocumentRecovery {

  private static final Logger log = LoggerFactory.getLogger(DocumentRecovery.class);

  private final DocumentMapper documentMapper;
  private final DocumentFileStorage fileStorage;
  private final DocumentTaskDispatcher taskDispatcher;
  private final KnowledgeProperties properties;

  public DocumentRecovery(DocumentMapper documentMapper, DocumentFileStorage fileStorage,
      DocumentTaskDispatcher taskDispatcher, KnowledgeProperties properties) {
    this.documentMapper = documentMapper;
    this.fileStorage = fileStorage;
    this.taskDispatcher = taskDispatcher;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    try {
      recoverPendingDocuments();
    } catch (Exception e) {
      log.error("document-recovery startup scan failed", e);
    }
  }

  private void recoverPendingDocuments() {
    int limit = Math.max(1, Math.min(properties.getRecoveryLimit(), 100));
    LocalDateTime cutoff = LocalDateTime.now().minus(properties.getProcessingTimeout());
    List<Document> candidates = documentMapper.selectList(Wrappers.<Document>lambdaQuery()
        .and(group -> group.eq(Document::getStatus, DocumentStatus.PENDING)
            .or(stale -> stale.eq(Document::getStatus, DocumentStatus.PROCESSING)
                .lt(Document::getUpdatedAt, cutoff)))
        .orderByAsc(Document::getId)
        .last("LIMIT " + limit));

    for (Document document : candidates) {
      if (DocumentStatus.PROCESSING.equals(document.getStatus())) {
        int reset = documentMapper.update(null, Wrappers.<Document>lambdaUpdate()
            .eq(Document::getId, document.getId())
            .eq(Document::getStatus, DocumentStatus.PROCESSING)
            .lt(Document::getUpdatedAt, cutoff)
            .set(Document::getStatus, DocumentStatus.PENDING)
            .set(Document::getUpdatedAt, LocalDateTime.now()));
        if (reset == 0) {
          continue;
        }
      }

      if (!fileStorage.exists(document.getKnowledgeBaseId(), document.getId(),
          document.getFileType())) {
        markMissing(document.getId());
        continue;
      }
      if (!taskDispatcher.submit(document.getId())) {
        log.warn("document-recovery queue full; remaining documents stay pending");
        break;
      }
    }
    if (!candidates.isEmpty()) {
      log.info("document-recovery candidates={}", candidates.size());
    }
  }

  private void markMissing(Long documentId) {
    documentMapper.update(null, Wrappers.<Document>lambdaUpdate()
        .eq(Document::getId, documentId)
        .eq(Document::getStatus, DocumentStatus.PENDING)
        .set(Document::getStatus, DocumentStatus.FAILED)
        .set(Document::getErrorMessage, "原始文件不存在，无法恢复处理")
        .set(Document::getChunkCount, 0)
        .set(Document::getUpdatedAt, LocalDateTime.now()));
  }
}
