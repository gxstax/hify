package com.hify.knowledge.processing;

import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** Submits document jobs to the explicitly configured async executor. */
@Component
public class DocumentTaskDispatcher {

  private final ThreadPoolTaskExecutor asyncExecutor;
  private final DocumentProcessor processor;

  public DocumentTaskDispatcher(
      @Qualifier("asyncExecutor") ThreadPoolTaskExecutor asyncExecutor,
      DocumentProcessor processor) {
    this.asyncExecutor = asyncExecutor;
    this.processor = processor;
  }

  public boolean submit(Long documentId) {
    try {
      asyncExecutor.execute(() -> processor.process(documentId));
      return true;
    } catch (RejectedExecutionException e) {
      return false;
    }
  }
}
