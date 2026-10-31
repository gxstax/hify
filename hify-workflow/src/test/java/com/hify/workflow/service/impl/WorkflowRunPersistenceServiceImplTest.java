package com.hify.workflow.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WorkflowRunPersistenceServiceImplTest {

  @Test
  void shouldTruncateErrorToFiveHundredCharacters() {
    String error = "x".repeat(600);

    assertEquals(error.substring(0, 500),
        WorkflowRunPersistenceServiceImpl.truncateError(error));
    assertEquals("short", WorkflowRunPersistenceServiceImpl.truncateError("short"));
    assertNull(WorkflowRunPersistenceServiceImpl.truncateError(null));
  }
}
