package com.hify.knowledge.service;

import com.hify.common.dto.PageResult;
import com.hify.knowledge.dto.DocumentChunkResponse;
import com.hify.knowledge.dto.DocumentResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Business facade for uploaded knowledge documents. */
public interface DocumentService {

  Long uploadDocument(Long knowledgeBaseId, MultipartFile file);

  PageResult<DocumentResponse> listDocuments(Long knowledgeBaseId, int page, int pageSize);

  DocumentResponse getDocument(Long id);

  List<DocumentChunkResponse> listChunks(Long documentId);

  void deleteDocument(Long id);
}
