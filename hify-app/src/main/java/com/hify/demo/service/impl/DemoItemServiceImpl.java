package com.hify.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.dto.PageResult;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.demo.dto.DemoItemCreateReq;
import com.hify.demo.dto.DemoItemResp;
import com.hify.demo.dto.DemoItemUpdateReq;
import com.hify.demo.entity.DemoItem;
import com.hify.demo.mapper.DemoItemMapper;
import com.hify.demo.service.DemoItemService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * DemoItem business logic. This is the reference shape for the real modules:
 * existence checks throw {@link BizException} with an {@link ErrorCode}, list
 * endpoints page through MyBatis-Plus and wrap into {@link PageResult}, and
 * nothing leaks to the controller except DTOs. Mapper is injected directly
 * (BaseMapper provides every CRUD primitive); auto-fill and logical delete
 * are handled by the MyBatis-Plus infrastructure in hify-common.
 */
@Service
@RequiredArgsConstructor
public class DemoItemServiceImpl implements DemoItemService {

  private final DemoItemMapper demoItemMapper;

  @Override
  public DemoItemResp createItem(DemoItemCreateReq req) {
    ensureNameFree(req.getName(), null);

    DemoItem item = new DemoItem();
    item.setName(req.getName());
    item.setStatus(req.getStatus());
    // createdAt/updatedAt auto-filled on insert; id back-filled by the mapper
    demoItemMapper.insert(item);
    return DemoItemResp.from(item);
  }

  @Override
  public DemoItemResp updateItem(Long id, DemoItemUpdateReq req) {
    DemoItem item = requireItem(id);
    ensureNameFree(req.getName(), id);

    item.setName(req.getName());
    item.setStatus(req.getStatus());
    // updatedAt auto-filled on update (in-place on this entity)
    demoItemMapper.updateById(item);
    return DemoItemResp.from(item);
  }

  @Override
  public DemoItemResp getItem(Long id) {
    return DemoItemResp.from(requireItem(id));
  }

  @Override
  public void deleteItem(Long id) {
    requireItem(id);
    // Logical delete: MyBatis-Plus turns this into `UPDATE ... SET deleted = 1`
    demoItemMapper.deleteById(id);
  }

  @Override
  public PageResult<DemoItemResp> listItems(int page, int pageSize) {
    Page<DemoItem> mpPage = PageHelper.toPage(page, pageSize);
    demoItemMapper.selectPage(mpPage, byIdDesc());

    List<DemoItemResp> list = mpPage.getRecords().stream().map(DemoItemResp::from).toList();
    return new PageResult<>(list, mpPage.getTotal(), (int) mpPage.getCurrent(), (int) mpPage.getSize());
  }

  private LambdaQueryWrapper<DemoItem> byIdDesc() {
    return Wrappers.<DemoItem>lambdaQuery().orderByDesc(DemoItem::getId);
  }

  /** Load by id or fail with a not-found error. */
  private DemoItem requireItem(Long id) {
    DemoItem item = demoItemMapper.selectById(id);
    if (item == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "记录不存在: id=" + id);
    }
    return item;
  }

  /** Reject a duplicate name; {@code excludeId} skips the item being updated. */
  private void ensureNameFree(String name, Long excludeId) {
    Long count = demoItemMapper.selectCount(Wrappers.<DemoItem>lambdaQuery()
        .eq(DemoItem::getName, name)
        .ne(excludeId != null, DemoItem::getId, excludeId));
    if (count != null && count > 0) {
      throw new BizException(ErrorCode.PARAM_ERROR, "名称已存在: " + name);
    }
  }
}
