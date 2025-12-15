package org.folio.rtaccache.service.handler.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class ItemUpdateEventHandler implements InventoryEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;
  private final FolioExecutionContext folioExecutionContext;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var tenantId = folioExecutionContext.getTenantId();
    var okapiUrl = folioExecutionContext.getOkapiUrl();
    log.info("Handling Item UPDATE event for tenant: {}, okapiUrl: {}", tenantId, okapiUrl);
    var item = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Item.class);
    holdingRepository.findByIdIdAndIdType(UUID.fromString(item.getId()), TypeEnum.ITEM)
      .ifPresent(existingItemEntity -> {
        log.info("Found existing RTAC holding for item id: {}", item.getId());
        var existingRtacHolding = existingItemEntity.getRtacHolding();
        var updatedRtacHolding = rtacHoldingMappingService.mapForItemTypeFrom(existingRtacHolding, item);
        log.info("Updating RTAC holding for item id: {}", item.getId());
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

