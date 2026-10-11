package com.hify.knowledge.controller;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.knowledge.dto.DocumentChunkResponse;
import com.hify.knowledge.dto.DocumentResponse;
import com.hify.knowledge.service.DocumentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** REST endpoints for uploaded knowledge documents. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService documentService;

  @PostMapping(value = "/knowledge-bases/{kbId}/documents",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Result<Long> upload(@PathVariable Long kbId,
      @RequestParam("file") MultipartFile file) {
    return Result.ok(documentService.uploadDocument(kbId, file));
  }

  @GetMapping("/knowledge-bases/{kbId}/documents")
  public Result<PageResult<DocumentResponse>> list(@PathVariable Long kbId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return Result.ok(documentService.listDocuments(kbId, page, pageSize));
  }

  @GetMapping("/documents/{id}")
  public Result<DocumentResponse> get(@PathVariable Long id) {
    return Result.ok(documentService.getDocument(id));
  }

  @GetMapping("/documents/{id}/chunks")
  public Result<List<DocumentChunkResponse>> chunks(@PathVariable Long id) {
    return Result.ok(documentService.listChunks(id));
  }

  @DeleteMapping("/documents/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    documentService.deleteDocument(id);
    return Result.ok();
  }
}
