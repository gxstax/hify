package com.hify.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.dto.PageResult;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.knowledge.constant.DocumentStatus;
import com.hify.knowledge.dto.DocumentChunkResponse;
import com.hify.knowledge.dto.DocumentResponse;
import com.hify.knowledge.entity.Document;
import com.hify.knowledge.entity.KnowledgeBase;
import com.hify.knowledge.mapper.DocumentMapper;
import com.hify.knowledge.mapper.KnowledgeBaseMapper;
import com.hify.knowledge.processing.DocumentTaskDispatcher;
import com.hify.knowledge.repository.DocumentChunkRepository;
import com.hify.knowledge.service.DocumentService;
import com.hify.knowledge.storage.DocumentFileStorage;
import com.hify.knowledge.storage.ValidatedUpload;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Document upload, metadata queries and cross-database logical deletion. */
@Service
public class DocumentServiceImpl implements DocumentService {

  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final DocumentMapper documentMapper;
  private final DocumentChunkRepository chunkRepository;
  private final DocumentFileStorage fileStorage;
  private final DocumentTaskDispatcher taskDispatcher;

  public DocumentServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper, DocumentMapper documentMapper,
      DocumentChunkRepository chunkRepository, DocumentFileStorage fileStorage,
      DocumentTaskDispatcher taskDispatcher) {
    this.knowledgeBaseMapper = knowledgeBaseMapper;
    this.documentMapper = documentMapper;
    this.chunkRepository = chunkRepository;
    this.fileStorage = fileStorage;
    this.taskDispatcher = taskDispatcher;
  }

  @Override
  public Long uploadDocument(Long knowledgeBaseId, MultipartFile file) {
    requireKnowledgeBase(knowledgeBaseId);
    ValidatedUpload upload = fileStorage.validate(file);

    Document document = new Document();
    document.setKnowledgeBaseId(knowledgeBaseId);
    document.setName(upload.fileName());
    document.setFileType(upload.fileType());
    document.setFileSize(upload.fileSize());
    document.setStatus(DocumentStatus.PENDING);
    document.setErrorMessage("");
    document.setChunkCount(0);
    documentMapper.insert(document);

    try {
      fileStorage.save(knowledgeBaseId, document.getId(), upload.fileType(), file);
    } catch (RuntimeException e) {
      documentMapper.deleteById(document.getId());
      throw e;
    }

    if (!taskDispatcher.submit(document.getId())) {
      markSubmissionFailed(document.getId());
      fileStorage.deleteBestEffort(knowledgeBaseId, document.getId(), upload.fileType());
    }
    return document.getId();
  }

  @Override
  public PageResult<DocumentResponse> listDocuments(Long knowledgeBaseId, int page, int pageSize) {
    requireKnowledgeBase(knowledgeBaseId);
    Page<Document> mpPage = PageHelper.toPage(page, pageSize);
    documentMapper.selectPage(mpPage, Wrappers.<Document>lambdaQuery()
        .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
        .orderByDesc(Document::getId));
    List<DocumentResponse> list = mpPage.getRecords().stream().map(DocumentResponse::from).toList();
    return new PageResult<>(list, mpPage.getTotal(), (int) mpPage.getCurrent(),
        (int) mpPage.getSize());
  }

  @Override
  public DocumentResponse getDocument(Long id) {
    return DocumentResponse.from(requireDocument(id));
  }

  @Override
  public List<DocumentChunkResponse> listChunks(Long documentId) {
    requireDocument(documentId);
    return chunkRepository.findByDocumentId(documentId).stream()
        .map(DocumentChunkResponse::from)
        .toList();
  }

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager")
  public void deleteDocument(Long id) {
    Document document = requireDocument(id);
    chunkRepository.logicalDeleteByDocumentId(id);
    documentMapper.deleteById(id);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        fileStorage.deleteBestEffort(document.getKnowledgeBaseId(), document.getId(),
            document.getFileType());
      }
    });
  }

  private KnowledgeBase requireKnowledgeBase(Long id) {
    KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(id);
    if (knowledgeBase == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在: id=" + id);
    }
    return knowledgeBase;
  }

  private Document requireDocument(Long id) {
    Document document = documentMapper.selectById(id);
    if (document == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "文档不存在: id=" + id);
    }
    return document;
  }

  private void markSubmissionFailed(Long documentId) {
    documentMapper.update(null, Wrappers.<Document>lambdaUpdate()
        .eq(Document::getId, documentId)
        .eq(Document::getStatus, DocumentStatus.PENDING)
        .set(Document::getStatus, DocumentStatus.FAILED)
        .set(Document::getErrorMessage, "处理队列已满，请稍后重新上传")
        .set(Document::getUpdatedAt, LocalDateTime.now()));
  }
}
