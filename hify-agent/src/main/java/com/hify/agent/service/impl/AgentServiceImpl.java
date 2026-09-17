package com.hify.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.agent.dto.AgentCreateReq;
import com.hify.agent.dto.AgentDetailResponse;
import com.hify.agent.dto.AgentListResponse;
import com.hify.agent.dto.AgentToolBindReq;
import com.hify.agent.dto.AgentUpdateReq;
import com.hify.agent.entity.Agent;
import com.hify.agent.entity.AgentTool;
import com.hify.agent.mapper.AgentMapper;
import com.hify.agent.mapper.AgentToolMapper;
import com.hify.agent.service.AgentService;
import com.hify.common.constant.CacheNames;
import com.hify.common.dto.PageResult;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.provider.dto.ModelConfigBrief;
import com.hify.provider.service.ModelConfigService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent business logic.
 *
 * <p>Cache: writes evict the whole {@code agent-cache} (paged list keys cannot
 * be invalidated precisely). Cross-module model access goes through the
 * provider module's {@link ModelConfigService} interface — never its mappers
 * or entities (CLAUDE.md).
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

  /** Context turns kept when the request omits the value (matches the DB default). */
  private static final int DEFAULT_MAX_CONTEXT_TURNS = 10;

  private final AgentMapper agentMapper;
  private final AgentToolMapper agentToolMapper;
  private final ModelConfigService modelConfigService;

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.AGENT, allEntries = true)
  public AgentDetailResponse createAgent(AgentCreateReq req) {
    // 1. Name must be unique among live agents (MP filters deleted = 0)
    ensureNameFree(req.getName());

    // 2. Model must exist and be usable (cross-module, Service interface only)
    ModelConfigBrief model = modelConfigService.requireUsable(req.getModelConfigId());

    // 3. Insert the agent (id back-filled, timestamps auto-filled)
    Agent agent = new Agent();
    agent.setName(req.getName());
    agent.setDescription(req.getDescription());
    agent.setSystemPrompt(req.getSystemPrompt());
    agent.setModelConfigId(req.getModelConfigId());
    agent.setTemperature(req.getTemperature());
    agent.setMaxTokens(req.getMaxTokens());
    agent.setMaxContextTurns(req.getMaxContextTurns() == null
        ? DEFAULT_MAX_CONTEXT_TURNS : req.getMaxContextTurns());
    agent.setEnabled(Boolean.TRUE);
    agentMapper.insert(agent);

    // 4. Bind tools in the same transaction. Note: tool ids are stored as-is;
    // existence validation needs the MCP module (not built yet) — add
    // mcpServerService.requireExists(...) here once it lands.
    List<Long> toolIds = normalizeToolIds(req.getToolIds());
    for (Long toolId : toolIds) {
      AgentTool binding = new AgentTool();
      binding.setAgentId(agent.getId());
      binding.setToolId(toolId);
      agentToolMapper.insert(binding);
    }

    return AgentDetailResponse.of(agent, model, toolIds);
  }

  @Override
  @Cacheable(cacheNames = CacheNames.AGENT, key = "'detail:' + #id")
  public AgentDetailResponse getAgent(Long id) {
    Agent agent = requireAgent(id);
    ModelConfigBrief model = modelConfigService
        .mapByIds(List.of(agent.getModelConfigId()))
        .get(agent.getModelConfigId());
    List<Long> toolIds = agentToolMapper.selectList(Wrappers.<AgentTool>lambdaQuery()
            .eq(AgentTool::getAgentId, id))
        .stream()
        .map(AgentTool::getToolId)
        .toList();
    return AgentDetailResponse.of(agent, model, toolIds);
  }

  @Override
  // Intentionally NOT cached: rows join model info and the tool count, and
  // every write evicts the whole cache anyway (same decision as provider list).
  public PageResult<AgentListResponse> listAgents(int page, int pageSize, String keyword,
      Boolean enabled) {
    LambdaQueryWrapper<Agent> wrapper = Wrappers.<Agent>lambdaQuery()
        .eq(enabled != null, Agent::getEnabled, enabled)
        .and(keyword != null && !keyword.isBlank(),
            w -> w.like(Agent::getName, keyword).or().like(Agent::getDescription, keyword))
        .orderByDesc(Agent::getId);

    Page<Agent> mpPage = PageHelper.toPage(page, pageSize);
    agentMapper.selectPage(mpPage, wrapper);
    List<Agent> agents = mpPage.getRecords();
    if (agents.isEmpty()) {
      return new PageResult<>(List.of(), mpPage.getTotal(), (int) mpPage.getCurrent(),
          (int) mpPage.getSize());
    }

    // Batch-enrich: model info (cross-module, one call) + tool counts (one call)
    List<Long> ids = agents.stream().map(Agent::getId).toList();
    Set<Long> modelIds = agents.stream()
        .map(Agent::getModelConfigId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Long, ModelConfigBrief> modelById = modelConfigService.mapByIds(modelIds);
    Map<Long, Long> toolCountByAgent = agentToolMapper.selectList(
            Wrappers.<AgentTool>lambdaQuery().in(AgentTool::getAgentId, ids))
        .stream()
        .collect(Collectors.groupingBy(AgentTool::getAgentId, Collectors.counting()));

    List<AgentListResponse> items = agents.stream()
        .map(a -> AgentListResponse.of(a, modelById.get(a.getModelConfigId()),
            toolCountByAgent.getOrDefault(a.getId(), 0L)))
        .toList();
    return new PageResult<>(items, mpPage.getTotal(), (int) mpPage.getCurrent(),
        (int) mpPage.getSize());
  }

  @Override
  @CacheEvict(cacheNames = CacheNames.AGENT, allEntries = true)
  public AgentDetailResponse updateAgent(Long id, AgentUpdateReq req) {
    Agent existing = requireAgent(id);
    ensureNameFree(req.getName(), id);

    // Validate the model only when it actually changed (saves a cross-module call)
    ModelConfigBrief model;
    if (!Objects.equals(existing.getModelConfigId(), req.getModelConfigId())) {
      model = modelConfigService.requireUsable(req.getModelConfigId());
    } else {
      model = modelConfigService.mapByIds(List.of(req.getModelConfigId()))
          .get(req.getModelConfigId());
    }

    Agent update = new Agent();
    update.setId(id);
    update.setName(req.getName());
    update.setDescription(req.getDescription());
    update.setSystemPrompt(req.getSystemPrompt());
    update.setModelConfigId(req.getModelConfigId());
    update.setTemperature(req.getTemperature());
    update.setMaxTokens(req.getMaxTokens());
    update.setMaxContextTurns(req.getMaxContextTurns());
    update.setEnabled(req.getEnabled());
    // null fields are skipped by MP's update strategy; updatedAt auto-filled
    agentMapper.updateById(update);

    Agent updated = requireAgent(id);
    List<Long> toolIds = agentToolMapper.selectList(Wrappers.<AgentTool>lambdaQuery()
            .eq(AgentTool::getAgentId, id))
        .stream().map(AgentTool::getToolId).toList();
    return AgentDetailResponse.of(updated, model, toolIds);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.AGENT, allEntries = true)
  public List<Long> updateAgentTools(Long id, AgentToolBindReq req) {
    requireAgent(id);
    List<Long> toolIds = normalizeToolIds(req.getToolIds());

    // Full replacement: purge old bindings physically (a logical delete would
    // keep uk_agent_tool occupied and break re-binding the same tool), then
    // insert the new set. Tool ids are stored as-is — existence validation
    // needs the MCP module, which is not built yet.
    agentToolMapper.deletePhysicallyByAgentId(id);
    for (Long toolId : toolIds) {
      AgentTool binding = new AgentTool();
      binding.setAgentId(id);
      binding.setToolId(toolId);
      agentToolMapper.insert(binding);
    }
    return toolIds;
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.AGENT, allEntries = true)
  public void deleteAgent(Long id) {
    requireAgent(id);
    agentMapper.deleteById(id);                       // logical delete
    agentToolMapper.deletePhysicallyByAgentId(id);    // bindings purged with the agent
  }

  /** Load the agent or fail with not-found. */
  private Agent requireAgent(Long id) {
    Agent agent = agentMapper.selectById(id);
    if (agent == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在: id=" + id);
    }
    return agent;
  }

  /** Reject a duplicate name among live agents. */
  private void ensureNameFree(String name) {
    Long count = agentMapper.selectCount(
        Wrappers.<Agent>lambdaQuery().eq(Agent::getName, name));
    if (count != null && count > 0) {
      throw new BizException(ErrorCode.PARAM_ERROR, "Agent 名称已存在: " + name);
    }
  }

  /** Reject a duplicate name, skipping the agent being updated. */
  private void ensureNameFree(String name, Long excludeId) {
    Long count = agentMapper.selectCount(Wrappers.<Agent>lambdaQuery()
        .eq(Agent::getName, name)
        .ne(Agent::getId, excludeId));
    if (count != null && count > 0) {
      throw new BizException(ErrorCode.PARAM_ERROR, "Agent 名称已存在: " + name);
    }
  }

  /** Collapse nulls/duplicates into a stable tool id list. */
  private List<Long> normalizeToolIds(List<Long> toolIds) {
    return toolIds == null ? List.of()
        : toolIds.stream().filter(Objects::nonNull).distinct().toList();
  }
}
