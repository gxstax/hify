package com.hify.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.agent.entity.AgentTool;
import com.hify.agent.mapper.AgentToolMapper;
import com.hify.mcp.service.McpToolReferenceChecker;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentMcpToolReferenceChecker implements McpToolReferenceChecker {

  private final AgentToolMapper agentToolMapper;

  @Override
  public boolean anyReferenced(Collection<Long> toolIds) {
    if (toolIds == null || toolIds.isEmpty()) {
      return false;
    }
    Long count = agentToolMapper.selectCount(Wrappers.<AgentTool>lambdaQuery()
        .in(AgentTool::getToolId, toolIds));
    return count != null && count > 0;
  }
}
