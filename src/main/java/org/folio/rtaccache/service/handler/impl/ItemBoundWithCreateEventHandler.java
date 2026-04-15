package org.folio.rtaccache.service.handler.impl;

import static java.util.UUID.fromString;

import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.BoundWithPart;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemBoundWithCreateEventHandler implements InventoryEventHandler {

  private final RtacHoldingRepository rtacHoldingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var boundWithPart = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, BoundWithPart.class);
    if (boundWithPart.getInstanceId() != null) {
      rtacHoldingRepository.deleteAllByIdInstanceId(fromString(boundWithPart.getInstanceId()));
    }
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.CREATE;
  }

  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.ITEM_BOUND_WITH;
  }
}
