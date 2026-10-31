package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.config.NodeConfigDef;

final class ExecutorSupport {

  private ExecutorSupport() {
  }

  static BizException wrap(String nodeKey, Exception exception) {
    ErrorCode errorCode = exception instanceof BizException bizException
        ? bizException.getErrorCode() : ErrorCode.SYSTEM_ERROR;
    String detail = exception.getMessage() == null || exception.getMessage().isBlank()
        ? exception.getClass().getSimpleName() : exception.getMessage();
    return new BizException(errorCode,
        "节点 " + nodeKey + " 执行失败: " + detail, exception);
  }

  static <T extends NodeConfigDef> T requireConfig(NodeConfigDef config, Class<T> type,
      String nodeType) {
    if (!type.isInstance(config)) {
      throw new BizException(ErrorCode.SYSTEM_ERROR,
          nodeType + " 节点配置类型错误");
    }
    return type.cast(config);
  }
}
