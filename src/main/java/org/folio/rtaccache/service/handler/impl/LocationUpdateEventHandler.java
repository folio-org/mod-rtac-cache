package org.folio.rtaccache.service.handler.impl;

import static org.folio.rtaccache.constant.RtacCacheConstant.LOCATIONS_CACHE_NAME;

import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.CacheUtil;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationUpdateEventHandler implements InventoryEventHandler {

  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;
  private final CacheUtil cacheUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    cacheUtil.clearCache(LOCATIONS_CACHE_NAME);
    var locationData = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Location.class);
    var updatedRtacHoldingEntities = holdingRepository.findAllByLocationId(locationData.getId())
      .stream()
      .map(rtacHoldingEntity -> {
        var rtacHolding = rtacHoldingEntity.getRtacHolding();
        var rtacLocation = rtacHolding.getLocation();
        rtacLocation.setName(locationData.getName());
        rtacLocation.setCode(locationData.getCode());
        return rtacHoldingEntity;
      })
      .toList();

    if (updatedRtacHoldingEntities.isEmpty()) {
      return;
    }

    holdingRepository.saveAll(updatedRtacHoldingEntities);
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.UPDATE;
  }

  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.LOCATION;
  }
}
