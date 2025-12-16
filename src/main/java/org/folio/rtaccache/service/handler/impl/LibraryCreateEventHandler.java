package org.folio.rtaccache.service.handler.impl;

import static org.folio.rtaccache.constant.RtacCacheConstant.LIBRARY_CACHE_NAME;

import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.CacheUtil;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LibraryCreateEventHandler implements InventoryEventHandler {

  private final CacheUtil cacheUtil;

  @Override
  public void handle(InventoryResourceEvent resourceEvent) {
    cacheUtil.clearCache(LIBRARY_CACHE_NAME);
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.CREATE;
  }

  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.LIBRARY;
  }
}
