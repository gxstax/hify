package com.hify.agent.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class AgentToolBindReqTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsAtMostTenToolsAndEmptyList() {
    assertEquals(0, validator.validate(request(List.of())).size());
    assertEquals(0, validator.validate(request(ids(10))).size());
  }

  @Test
  void rejectsMoreThanTenTools() {
    assertEquals(1, validator.validate(request(ids(11))).size());
  }

  private AgentToolBindReq request(List<Long> toolIds) {
    AgentToolBindReq req = new AgentToolBindReq();
    req.setToolIds(toolIds);
    return req;
  }

  private List<Long> ids(int count) {
    return LongStream.rangeClosed(1, count).boxed().toList();
  }
}
