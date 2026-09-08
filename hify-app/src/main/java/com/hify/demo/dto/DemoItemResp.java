package com.hify.demo.dto;

import com.hify.demo.entity.DemoItem;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Response payload of a DemoItem. Never expose the entity directly — map it
 * explicitly so the API surface stays stable when the entity grows.
 */
@Data
public class DemoItemResp {

  private Long id;
  private String name;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** Map an entity to its response DTO. */
  public static DemoItemResp from(DemoItem item) {
    DemoItemResp resp = new DemoItemResp();
    resp.setId(item.getId());
    resp.setName(item.getName());
    resp.setStatus(item.getStatus());
    resp.setCreatedAt(item.getCreatedAt());
    resp.setUpdatedAt(item.getUpdatedAt());
    return resp;
  }
}
