package com.hify.provider.adapter;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link ProviderAdapter} for a provider type. Registration is
 * automatic: every adapter is a Spring bean and the constructor collects them,
 * indexing by {@link ProviderAdapter#supportedTypes()}.
 *
 * <p>Adding a provider type therefore means: implement an adapter, declare its
 * types, and keep the type enum synchronized (see .claude/skills/provider-adapter).
 */
@Component
public class ProviderAdapterFactory {

  private final Map<String, ProviderAdapter> adaptersByType;

  public ProviderAdapterFactory(List<ProviderAdapter> adapters) {
    Map<String, ProviderAdapter> index = new HashMap<>();
    for (ProviderAdapter adapter : adapters) {
      for (String type : adapter.supportedTypes()) {
        index.put(type.toUpperCase(Locale.ROOT), adapter);
      }
    }
    this.adaptersByType = Map.copyOf(index);
  }

  /**
   * Adapter for the given provider type (case-insensitive).
   *
   * @throws BizException when the type has no adapter — a configuration error,
   *         not a connectivity failure
   */
  public ProviderAdapter get(String type) {
    ProviderAdapter adapter = type == null ? null
        : adaptersByType.get(type.toUpperCase(Locale.ROOT));
    if (adapter == null) {
      throw new BizException(ErrorCode.PARAM_ERROR, "不支持的供应商类型: " + type);
    }
    return adapter;
  }
}
