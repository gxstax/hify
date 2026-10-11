package com.hify.knowledge.controller;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.knowledge.dto.KnowledgeBaseCreateReq;
import com.hify.knowledge.dto.KnowledgeBaseResponse;
import com.hify.knowledge.dto.KnowledgeBaseUpdateReq;
import com.hify.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for knowledge-base management. */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

  private final KnowledgeBaseService knowledgeBaseService;

  @PostMapping
  public Result<KnowledgeBaseResponse> create(
      @Valid @RequestBody KnowledgeBaseCreateReq req) {
    return Result.ok(knowledgeBaseService.createKnowledgeBase(req));
  }

  @GetMapping
  public Result<PageResult<KnowledgeBaseResponse>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String name) {
    return Result.ok(knowledgeBaseService.listKnowledgeBases(page, size, name));
  }

  @GetMapping("/{id}")
  public Result<KnowledgeBaseResponse> get(@PathVariable Long id) {
    return Result.ok(knowledgeBaseService.getKnowledgeBase(id));
  }

  @PutMapping("/{id}")
  public Result<KnowledgeBaseResponse> update(@PathVariable Long id,
      @Valid @RequestBody KnowledgeBaseUpdateReq req) {
    return Result.ok(knowledgeBaseService.updateKnowledgeBase(id, req));
  }

  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    knowledgeBaseService.deleteKnowledgeBase(id);
    return Result.ok();
  }
}
