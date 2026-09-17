package com.hify.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.agent.entity.AgentTool;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/** Mapper for {@link AgentTool} bindings. */
public interface AgentToolMapper extends BaseMapper<AgentTool> {

  /**
   * Hard-delete all bindings of an agent — used by full-replacement rebinding
   * and by agent deletion.
   *
   * <p>Deliberately bypasses logical delete: a logically-deleted row still
   * occupies {@code uk_agent_tool (agent_id, tool_id)}, so re-binding the same
   * tool later would fail with a duplicate-key error. Bindings carry no audit
   * value, so physical removal is the correct semantic here.
   */
  @Delete("DELETE FROM agent_tool WHERE agent_id = #{agentId}")
  int deletePhysicallyByAgentId(@Param("agentId") Long agentId);
}
