package com.hify.demo.service;

import com.hify.common.dto.PageResult;
import com.hify.demo.dto.DemoItemCreateReq;
import com.hify.demo.dto.DemoItemResp;
import com.hify.demo.dto.DemoItemUpdateReq;

/**
 * Business facade of DemoItem. All business rules live in the implementation;
 * controllers only validate parameters and delegate here.
 *
 * <p>Plain three-layer shape (Controller -> Service -> Mapper): the service
 * injects the Mapper directly instead of MyBatis-Plus {@code IService}, whose
 * package moved in newer MP releases and which would hide the wiring anyway.
 */
public interface DemoItemService {

  /** Create an item; name must not collide with an existing one. */
  DemoItemResp createItem(DemoItemCreateReq req);

  /** Full-field update of the item with the given id. */
  DemoItemResp updateItem(Long id, DemoItemUpdateReq req);

  /** Read one item; throws BizException(NOT_FOUND) when absent. */
  DemoItemResp getItem(Long id);

  /** Delete one item (logical delete). */
  void deleteItem(Long id);

  /** Page through items, newest first. */
  PageResult<DemoItemResp> listItems(int page, int pageSize);
}
