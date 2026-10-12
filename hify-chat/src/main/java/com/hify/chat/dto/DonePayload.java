package com.hify.chat.dto;

import com.hify.knowledge.dto.RagCitation;
import java.util.List;

/** Terminal SSE event for a completed and persisted assistant reply. */
public record DonePayload(Long messageId, String finishReason,
    Integer promptTokens, Integer completionTokens, List<RagCitation> citations) {

  public DonePayload {
    citations = citations == null ? List.of() : List.copyOf(citations);
  }
}
