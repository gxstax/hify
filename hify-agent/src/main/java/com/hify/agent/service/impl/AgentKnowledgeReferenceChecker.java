package com.hify.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.agent.entity.Agent;
import com.hify.agent.mapper.AgentMapper;
import com.hify.knowledge.service.KnowledgeReferenceChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reports active Agent bindings to the knowledge module without exposing AgentService internals. */
@Component
@RequiredArgsConstructor
public class AgentKnowledgeReferenceChecker implements KnowledgeReferenceChecker {

  private final AgentMapper agentMapper;

  @Override
  public boolean isReferenced(Long knowledgeBaseId) {
    if (knowledgeBaseId == null) {
      return false;
    }
    Long count = agentMapper.selectCount(Wrappers.<Agent>lambdaQuery()
        .eq(Agent::getKnowledgeBaseId, knowledgeBaseId));
    return count != null && count > 0;
  }
}
