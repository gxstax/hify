package com.hify.provider.controller;

import com.hify.common.dto.Result;
import com.hify.provider.dto.ModelConfigBrief;
import com.hify.provider.service.ModelConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Model config read endpoints. Not paged on purpose: the model count stays
 * small and the picker wants the whole list in one call.
 */
@RestController
@RequestMapping("/api/v1/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {

  private final ModelConfigService modelConfigService;

  /** GET /api/v1/model-configs — bindable models (enabled models under enabled providers). */
  @GetMapping
  public Result<List<ModelConfigBrief>> list() {
    return Result.ok(modelConfigService.listAvailable());
  }
}
