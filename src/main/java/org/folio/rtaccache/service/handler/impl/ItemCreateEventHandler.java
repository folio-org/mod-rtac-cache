package org.folio.rtaccache.service.handler.impl;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
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
@Log4j2
public class ItemCreateEventHandler implements InventoryEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    log.info("Handling Item CREATE event for item id: {}", resourceEvent.getNew());
    var item = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Item.class);
    holdingRepository.findByIdIdAndIdType(UUID.fromString(item.getHoldingsRecordId()), TypeEnum.HOLDING)
      .ifPresent(existingHoldingsEntity -> {
        log.info("Found existing RTAC holding for holdingsRecordId: {}", item.getHoldingsRecordId());
        var existingRtacHolding = existingHoldingsEntity.getRtacHolding();
        var newRtacHoldingId = createRtacHoldingIdFromItem(item, existingRtacHolding.getInstanceId());
        var newRtacHolding = rtacHoldingMappingService.mapForItemTypeFrom(existingRtacHolding, item);
        log.info("Creating new RTAC holding for item id: {}", item.getId());
        var newHoldingEntity = new RtacHoldingEntity();
        newHoldingEntity.setId(newRtacHoldingId);
        newHoldingEntity.setCreatedAt(Instant.now());
        newHoldingEntity.setRtacHolding(newRtacHolding);
        holdingRepository.save(newHoldingEntity);
      });
  }

  private RtacHoldingId createRtacHoldingIdFromItem(Item item, String instanceId) {
    var newRtacHoldingId = new RtacHoldingId();
    newRtacHoldingId.setId(UUID.fromString(item.getId()));
    newRtacHoldingId.setType(TypeEnum.ITEM);
    newRtacHoldingId.setInstanceId(UUID.fromString(instanceId));
    return newRtacHoldingId;
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.CREATE;
  }

  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.ITEM;
  }
}
