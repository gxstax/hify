package com.hify.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Create request for {@code POST /api/v1/demo-items}. */
@Data
public class DemoItemCreateReq {

  @NotBlank(message = "name 不能为空")
  @Size(max = 128, message = "name 长度不能超过 128")
  private String name;

  @NotNull(message = "status 不能为空")
  private Integer status;
}
