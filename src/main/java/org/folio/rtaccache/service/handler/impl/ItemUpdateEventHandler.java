package org.folio.rtaccache.service.handler.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemUpdateEventHandler implements InventoryEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var item = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Item.class);
    holdingRepository.findByIdIdAndIdType(UUID.fromString(item.getId()), TypeEnum.ITEM)
      .ifPresent(existingItemEntity -> {
        var existingRtacHolding = existingItemEntity.getRtacHolding();
        var updatedRtacHolding = rtacHoldingMappingService.mapForItemTypeFrom(existingRtacHolding, item);
        existingItemEntity.setRtacHolding(updatedRtacHolding);
        holdingRepository.save(existingItemEntity);
      });
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.UPDATE;
  }

  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.ITEM;
  }
}

