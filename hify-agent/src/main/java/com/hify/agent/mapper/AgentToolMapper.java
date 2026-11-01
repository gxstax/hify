package com.hify.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.agent.entity.AgentTool;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/** Mapper for {@link AgentTool} bindings. */
public interface AgentToolMapper extends BaseMapper<AgentTool> {

  /** Hard-delete all bindings for full-replacement rebinding or Agent deletion. */
  @Delete("DELETE FROM agent_tool WHERE agent_id = #{agentId}")
  int deletePhysicallyByAgentId(@Param("agentId") Long agentId);
}
