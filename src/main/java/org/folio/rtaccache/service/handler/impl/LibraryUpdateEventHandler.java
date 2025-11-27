package org.folio.rtaccache.service.handler.impl;

import static org.folio.rtaccache.constant.RtacCacheConstant.LIBRARY_CACHE_NAME;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.CacheUtil;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryUpdateEventHandler implements InventoryEventHandler {

  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;
  private final CacheUtil cacheUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    cacheUtil.clearCache(LIBRARY_CACHE_NAME);
    var libraryData = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Loclib.class);
    var updatedRtacHoldingEntities = holdingRepository.findAllByLibraryId(libraryData.getId())
      .stream()
      .map(rtacHoldingEntity -> {
        var rtacHolding = rtacHoldingEntity.getRtacHolding();
        var rtacLibrary = rtacHolding.getLibrary();
        rtacLibrary.setName(libraryData.getName());
        rtacLibrary.setCode(libraryData.getCode());
        return rtacHoldingEntity;
      })
      .toList();

    if (CollectionUtils.isNotEmpty(updatedRtacHoldingEntities)) {
      holdingRepository.saveAll(updatedRtacHoldingEntities);
    }
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.UPDATE;
  }

  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.LIBRARY;
  }
}
