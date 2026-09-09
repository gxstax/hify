package com.hify.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Update request for {@code PUT /api/v1/providers/{id}}. Full-field update of
 * the editable columns; {@code apiKey} blank means "keep the stored key"
 * (responses never echo the key back, so the client cannot resend it).
 */
@Data
public class ProviderUpdateReq {

  @NotBlank(message = "供应商名称不能为空")
  @Size(max = 100, message = "供应商名称长度不能超过 100")
  private String name;

  @NotBlank(message = "供应商类型不能为空")
  @Pattern(regexp = "OPENAI|ANTHROPIC|OLLAMA|OPENAI_COMPATIBLE",
      message = "不支持的供应商类型（OPENAI / ANTHROPIC / OLLAMA / OPENAI_COMPATIBLE）")
  private String type;

  @NotBlank(message = "API 基础地址不能为空")
  @Size(max = 500, message = "API 基础地址长度不能超过 500")
  private String baseUrl;

  /** New API key; blank keeps the stored value. */
  @Size(max = 512, message = "API Key 长度不能超过 512")
  private String apiKey;

  @Size(max = 255, message = "备注长度不能超过 255")
  private String description;

  /** Null keeps the current value. */
  private Boolean enabled;
}
