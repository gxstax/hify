package com.hify.provider.controller;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.dto.ProviderCreateReq;
import com.hify.provider.dto.ProviderDetailResp;
import com.hify.provider.dto.ProviderResp;
import com.hify.provider.dto.ProviderUpdateReq;
import com.hify.provider.entity.Provider;
import com.hify.provider.service.ProviderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider REST endpoints (CLAUDE.md conventions: /api/v1/{resource},
 * unified Result envelope, page starts at 1 / pageSize default 20 max 100).
 *
 * <p>Request bodies are validated DTOs; responses are sanitized DTOs —
 * authConfig (API keys) never leaves the server.
 */
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

  private final ProviderService providerService;

  /** POST /api/v1/providers */
  @PostMapping
  public Result<ProviderResp> create(@Valid @RequestBody ProviderCreateReq req) {
    return Result.ok(ProviderResp.from(providerService.createProvider(toEntity(req))));
  }

  /** GET /api/v1/providers?page=1&pageSize=20&type=OPENAI&enabled=true */
  @GetMapping
  public Result<PageResult<ProviderResp>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Boolean enabled) {
    PageResult<Provider> paged = providerService.listProviders(page, pageSize, type, enabled);
    List<ProviderResp> items = paged.getList().stream().map(ProviderResp::from).toList();
    return Result.ok(new PageResult<>(items, paged.getTotal(), paged.getPage(), paged.getPageSize()));
  }

  /** GET /api/v1/providers/{id} — provider + modelConfigs + health */
  @GetMapping("/{id}")
  public Result<ProviderDetailResp> detail(@PathVariable Long id) {
    return Result.ok(ProviderDetailResp.from(providerService.getDetail(id)));
  }

  /** PUT /api/v1/providers/{id} */
  @PutMapping("/{id}")
  public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProviderUpdateReq req) {
    providerService.updateProvider(id, toUpdateEntity(req));
    return Result.ok();
  }

  /** DELETE /api/v1/providers/{id} — cascades to model configs + health */
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    providerService.deleteProvider(id);
    return Result.ok();
  }

  /** POST /api/v1/providers/{id}/test-connection */
  @PostMapping("/{id}/test-connection")
  public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
    return Result.ok(providerService.testConnection(id));
  }

  // ------------------------------------------------------------------
  // Request -> entity mapping (auth_config assembly)
  // ------------------------------------------------------------------

  private Provider toEntity(ProviderCreateReq req) {
    Provider provider = new Provider();
    provider.setName(req.getName());
    provider.setType(req.getType());
    provider.setBaseUrl(req.getBaseUrl());
    provider.setDescription(req.getDescription());
    provider.setAuthConfig(buildAuthConfig(req.getApiKey()));
    provider.setEnabled(Boolean.TRUE);
    return provider;
  }

  private Provider toUpdateEntity(ProviderUpdateReq req) {
    Provider provider = new Provider();
    provider.setName(req.getName());
    provider.setType(req.getType());
    provider.setBaseUrl(req.getBaseUrl());
    provider.setDescription(req.getDescription());
    // Blank apiKey means "keep the stored key"; non-blank replaces it.
    if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
      provider.setAuthConfig(buildAuthConfig(req.getApiKey()));
    }
    provider.setEnabled(req.getEnabled());
    return provider;
  }

  private Map<String, Object> buildAuthConfig(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      return null;
    }
    return Map.of("apiKey", apiKey);
  }
}
