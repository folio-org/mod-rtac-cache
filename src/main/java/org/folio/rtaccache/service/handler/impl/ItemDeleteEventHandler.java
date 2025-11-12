package org.folio.rtaccache.service.handler.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemDeleteEventHandler implements InventoryEventHandler {

  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var item = resourceEventUtil.getOldFromInventoryEvent(resourceEvent, Item.class);
    holdingRepository.deleteByIdId(UUID.fromString(item.getId()));
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.DELETE;
  }

  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.ITEM;
  }
}

