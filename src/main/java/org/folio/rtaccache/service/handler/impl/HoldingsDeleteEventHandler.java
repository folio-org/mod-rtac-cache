package org.folio.rtaccache.service.handler.impl;

import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
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
public class HoldingsDeleteEventHandler implements InventoryEventHandler {

  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var holdingsData = resourceEventUtil.getOldFromInventoryEvent(resourceEvent, HoldingsRecord.class);
    holdingRepository.deleteAllByHoldingsId(holdingsData.getId());
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.DELETE;
  }


  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.HOLDINGS;
  }

}
