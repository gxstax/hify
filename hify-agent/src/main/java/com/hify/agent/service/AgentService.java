package com.hify.agent.service;

import com.hify.agent.dto.AgentCreateReq;
import com.hify.agent.dto.AgentDetailResponse;
import com.hify.agent.dto.AgentListResponse;
import com.hify.agent.dto.AgentToolBindReq;
import com.hify.agent.dto.AgentUpdateReq;
import com.hify.common.dto.PageResult;
import java.util.List;

/** Business facade of agents. Controllers only validate and delegate here. */
public interface AgentService {

  /**
   * Create an agent and its tool bindings in one transaction.
   *
   * <p>Steps: name uniqueness -> model validation (via the provider module's
   * ModelConfigService) -> insert agent + agent_tool -> evict agent cache.
   */
  AgentDetailResponse createAgent(AgentCreateReq req);

  /** Detail of one agent: full fields + bound model info + bound tool ids. */
  AgentDetailResponse getAgent(Long id);

  /**
   * Page through agents (newest first), optionally filtered by keyword
   * (name/description) and enabled. Rows carry model info and tool count.
   */
  PageResult<AgentListResponse> listAgents(int page, int pageSize, String keyword, Boolean enabled);

  /** Full-field update of the basic info; model validation runs only when it changed. */
  AgentDetailResponse updateAgent(Long id, AgentUpdateReq req);

  /** Replace the agent's tool bindings wholesale; returns the new tool ids. */
  List<Long> updateAgentTools(Long id, AgentToolBindReq req);

  /** Logical delete of the agent; its tool bindings are purged. */
  void deleteAgent(Long id);
}
