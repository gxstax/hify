package com.hify.knowledge.dto;

import java.util.List;

/** Temporary prompt context plus citations safe to persist with the assistant message. */
public record RagRetrievalResult(String context, List<RagCitation> citations) {

  public RagRetrievalResult {
    context = context == null ? "" : context;
    citations = citations == null ? List.of() : List.copyOf(citations);
  }
}
