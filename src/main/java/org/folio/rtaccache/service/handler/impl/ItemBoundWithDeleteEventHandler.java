package org.folio.rtaccache.service.handler.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.BoundWithPart;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemBoundWithDeleteEventHandler implements InventoryEventHandler {

  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var boundWithPart = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, BoundWithPart.class);
    var rtacHoldingId = new RtacHoldingId();
    rtacHoldingId.setId(UUID.fromString(boundWithPart.getItemId()));
    rtacHoldingId.setType(TypeEnum.ITEM);
    rtacHoldingId.setInstanceId(UUID.fromString(boundWithPart.getInstanceId()));
    holdingRepository.findById(rtacHoldingId).ifPresent(existingItemEntity -> {
      var itemRtacHolding = existingItemEntity.getRtacHolding();
      if (itemRtacHolding.getIsBoundWith()) {
        holdingRepository.deleteById(rtacHoldingId);
      }
    });
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.DELETE;
  }

  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.ITEM_BOUND_WITH;
  }
}
