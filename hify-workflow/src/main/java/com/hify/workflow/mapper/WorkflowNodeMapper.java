package com.hify.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.entity.WorkflowNode;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkflowNodeMapper extends BaseMapper<WorkflowNode> {

  int insertBatch(@Param("items") List<WorkflowNode> items);
}
