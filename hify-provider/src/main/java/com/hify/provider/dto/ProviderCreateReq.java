package com.hify.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Create request for {@code POST /api/v1/providers}. */
@Data
public class ProviderCreateReq {

  @NotBlank(message = "供应商名称不能为空")
  @Size(max = 100, message = "供应商名称长度不能超过 100")
  private String name;

  @NotBlank(message = "供应商类型不能为空")
  @Pattern(regexp = "OPENAI|ANTHROPIC|DEEPSEEK|OLLAMA|OPENAI_COMPATIBLE",
      message = "不支持的供应商类型（OPENAI / ANTHROPIC / DEEPSEEK / OLLAMA / OPENAI_COMPATIBLE）")
  private String type;

  @NotBlank(message = "API 基础地址不能为空")
  @Size(max = 500, message = "API 基础地址长度不能超过 500")
  private String baseUrl;

  /** Plain API key; stored inside auth_config. Null for auth-free types (OLLAMA). */
  @Size(max = 512, message = "API Key 长度不能超过 512")
  private String apiKey;

  @Size(max = 255, message = "备注长度不能超过 255")
  private String description;
}
