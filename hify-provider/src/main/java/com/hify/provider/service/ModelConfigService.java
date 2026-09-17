package com.hify.provider.service;

import com.hify.provider.dto.ModelConfigBrief;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Cross-module facade for model configs. Other modules (agent, ...) must use
 * this interface — never the ModelConfigMapper or entity — per CLAUDE.md.
 */
public interface ModelConfigService {

  /**
   * Validate that the model can be bound right now and return its brief.
   *
   * @throws com.hify.common.exception.BizException NOT_FOUND when the model
   *         does not exist; PARAM_ERROR when the model or its provider is
   *         disabled
   */
  ModelConfigBrief requireUsable(Long id);

  /**
   * Batch-load briefs by id for display purposes (list/detail enrichment,
   * avoids N+1). Unlike {@link #requireUsable} it tolerates disabled models —
   * historical bindings must still render. Missing ids are simply absent from
   * the result; a deleted provider yields a brief with null providerName.
   */
  Map<Long, ModelConfigBrief> mapByIds(Collection<Long> ids);

  /**
   * All bindable models: enabled models under enabled providers, newest
   * first. Feeds the model picker in binding forms (agent, ...).
   */
  List<ModelConfigBrief> listAvailable();
}
