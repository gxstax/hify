package com.hify.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.entity.WorkflowEdge;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkflowEdgeMapper extends BaseMapper<WorkflowEdge> {

  int insertBatch(@Param("items") List<WorkflowEdge> items);
}
