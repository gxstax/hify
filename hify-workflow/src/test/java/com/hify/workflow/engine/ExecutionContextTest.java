package com.hify.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExecutionContextTest {

  @Test
  void shouldResolveNodeVariable() {
    ExecutionContext context = new ExecutionContext(1L, "你好");
    context.set("classify", "intent", "售后");

    String resolved = context.resolve("你好，{{classify.intent}}客服为您服务");

    assertEquals("你好，售后客服为您服务", resolved);
  }
}
