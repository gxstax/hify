package com.hify.demo.controller;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.demo.dto.DemoItemCreateReq;
import com.hify.demo.dto.DemoItemResp;
import com.hify.demo.dto.DemoItemUpdateReq;
import com.hify.demo.service.DemoItemService;
import jakarta.validation.Valid;
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
 * DemoItem REST endpoints. Reference shape for real controllers: parameter
 * validation only (paging bounds are handled in PageHelper), everything else
 * delegated to the service.
 */
@RestController
@RequestMapping("/api/v1/demo-items")
@RequiredArgsConstructor
public class DemoItemController {

  private final DemoItemService demoItemService;

  /** GET /api/v1/demo-items?page=1&pageSize=20 */
  @GetMapping
  public Result<PageResult<DemoItemResp>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return Result.ok(demoItemService.listItems(page, pageSize));
  }

  /** GET /api/v1/demo-items/{id} */
  @GetMapping("/{id}")
  public Result<DemoItemResp> get(@PathVariable Long id) {
    return Result.ok(demoItemService.getItem(id));
  }

  /** POST /api/v1/demo-items */
  @PostMapping
  public Result<DemoItemResp> create(@Valid @RequestBody DemoItemCreateReq req) {
    return Result.ok(demoItemService.createItem(req));
  }

  /** PUT /api/v1/demo-items/{id} */
  @PutMapping("/{id}")
  public Result<DemoItemResp> update(@PathVariable Long id,
      @Valid @RequestBody DemoItemUpdateReq req) {
    return Result.ok(demoItemService.updateItem(id, req));
  }

  /** DELETE /api/v1/demo-items/{id} */
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    demoItemService.deleteItem(id);
    return Result.ok();
  }
}
