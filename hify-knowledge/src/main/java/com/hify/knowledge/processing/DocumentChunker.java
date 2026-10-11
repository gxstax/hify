package com.hify.knowledge.processing;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.knowledge.config.KnowledgeProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Splits text with cl100k_base token limits and deterministic token overlap. */
@Component
public class DocumentChunker {

  private final int chunkSize;
  private final int chunkOverlap;
  private final Encoding encoding;

  public DocumentChunker(KnowledgeProperties properties) {
    this.chunkSize = properties.getChunkSize();
    this.chunkOverlap = properties.getChunkOverlap();
    this.encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    if (chunkSize <= 0 || chunkOverlap < 0 || chunkOverlap >= chunkSize) {
      throw new IllegalArgumentException("Invalid knowledge chunk-size/chunk-overlap configuration");
    }
  }

  public List<ChunkDTO> split(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.isEmpty()) {
      throw new BizException(ErrorCode.PARAM_ERROR, "文档中未提取到可处理文本");
    }

    List<ChunkDTO> chunks = new ArrayList<>();
    int start = 0;
    while (start < normalized.length()) {
      int hardEnd = findTokenLimitedEnd(normalized, start);
      int end = hardEnd == normalized.length()
          ? hardEnd : findPreferredBoundary(normalized, start, hardEnd);
      if (end <= start) {
        end = hardEnd;
      }
      String content = normalized.substring(start, end).strip();
      if (!content.isEmpty()) {
        int tokenCount = countTokens(content);
        if (tokenCount > chunkSize) {
          throw new IllegalStateException("Chunk exceeds configured token limit");
        }
        chunks.add(new ChunkDTO(chunks.size(), content, tokenCount, null));
      }
      if (end >= normalized.length()) {
        break;
      }
      int nextStart = findOverlapStart(normalized, start, end);
      start = nextStart > start ? nextStart : nextCodePointIndex(normalized, start);
    }
    if (chunks.isEmpty()) {
      throw new BizException(ErrorCode.PARAM_ERROR, "文档分块结果为空");
    }
    return chunks;
  }

  private int findTokenLimitedEnd(String text, int start) {
    int low = nextCodePointIndex(text, start);
    int high = text.length();
    int best = low;
    while (low <= high) {
      int middle = validBoundaryAtOrBefore(text, low + (high - low) / 2);
      if (middle <= start) {
        middle = nextCodePointIndex(text, start);
      }
      if (countTokens(text.substring(start, middle)) <= chunkSize) {
        best = middle;
        low = middle + 1;
      } else {
        high = middle - 1;
      }
    }
    while (best > start && countTokens(text.substring(start, best)) > chunkSize) {
      best = previousCodePointIndex(text, best);
    }
    return best;
  }

  private int findPreferredBoundary(String text, int start, int hardEnd) {
    int paragraph = text.lastIndexOf("\n\n", hardEnd - 1);
    if (paragraph >= start && isProgressBoundary(text, start, paragraph + 2)) {
      return paragraph + 2;
    }
    for (int index = hardEnd; index > start;) {
      int codePoint = text.codePointBefore(index);
      if (isSentenceEnd(codePoint) && isProgressBoundary(text, start, index)) {
        return index;
      }
      index -= Character.charCount(codePoint);
    }
    return hardEnd;
  }

  private boolean isProgressBoundary(String text, int start, int end) {
    return countTokens(text.substring(start, end)) > chunkOverlap;
  }

  private int findOverlapStart(String text, int chunkStart, int end) {
    if (chunkOverlap == 0) {
      return end;
    }
    int low = chunkStart;
    int high = end;
    int best = end;
    while (low <= high) {
      int middle = validBoundaryAtOrAfter(text, low + (high - low) / 2);
      if (middle > end) {
        middle = end;
      }
      int tokens = countTokens(text.substring(middle, end));
      if (tokens <= chunkOverlap) {
        best = middle;
        high = middle - 1;
      } else {
        low = middle + 1;
      }
    }
    return best;
  }

  private int countTokens(String text) {
    return encoding.countTokens(text);
  }

  private boolean isSentenceEnd(int codePoint) {
    return codePoint == '。' || codePoint == '！' || codePoint == '？'
        || codePoint == '.' || codePoint == '!' || codePoint == '?'
        || codePoint == '；' || codePoint == ';';
  }

  private int validBoundaryAtOrBefore(String text, int index) {
    int value = Math.max(0, Math.min(index, text.length()));
    if (value > 0 && value < text.length()
        && Character.isLowSurrogate(text.charAt(value))) {
      return value - 1;
    }
    return value;
  }

  private int validBoundaryAtOrAfter(String text, int index) {
    int value = Math.max(0, Math.min(index, text.length()));
    if (value > 0 && value < text.length()
        && Character.isLowSurrogate(text.charAt(value))) {
      return value + 1;
    }
    return value;
  }

  private int nextCodePointIndex(String text, int index) {
    if (index >= text.length()) {
      return text.length();
    }
    return index + Character.charCount(text.codePointAt(index));
  }

  private int previousCodePointIndex(String text, int index) {
    if (index <= 0) {
      return 0;
    }
    return index - Character.charCount(text.codePointBefore(index));
  }
}
