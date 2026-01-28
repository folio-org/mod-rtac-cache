package org.folio.rtaccache.service.handler.impl;

import static java.util.UUID.fromString;
import static org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum.HOLDING;
import static org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum.ITEM;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.BoundWithPart;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemBoundWithCreateEventHandler implements InventoryEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository rtacHoldingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var boundWithPart = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, BoundWithPart.class);
    rtacHoldingRepository.findByIdIdAndIdType(fromString(boundWithPart.getItemId()), ITEM)
      .ifPresent(existingItemEntity -> {
        var itemRtacHolding = existingItemEntity.getRtacHolding();
        if (StringUtils.equals(itemRtacHolding.getHoldingsId(), boundWithPart.getHoldingsRecordId())) {
          return;
        }
        rtacHoldingRepository.findByIdIdAndIdType(fromString(boundWithPart.getHoldingsRecordId()), HOLDING)
          .ifPresent(existingHoldingsEntity -> {
            var holdingsRtacHolding = existingHoldingsEntity.getRtacHolding();
            var boundWithRtacHolding = rtacHoldingMappingService.mapForBoundWithItemTypeFrom(holdingsRtacHolding,
              itemRtacHolding);
            var boundWithRtacHoldingEntity = new RtacHoldingEntity();
            var boundWithRtacHoldingId = getRtacHoldingId(boundWithPart);
            boundWithRtacHoldingEntity.setId(boundWithRtacHoldingId);
            boundWithRtacHoldingEntity.setShared(existingHoldingsEntity.isShared());
            boundWithRtacHoldingEntity.setCreatedAt(existingItemEntity.getCreatedAt());
            boundWithRtacHoldingEntity.setRtacHolding(boundWithRtacHolding);
            rtacHoldingRepository.save(boundWithRtacHoldingEntity);
          });
    });
  }

  private RtacHoldingId getRtacHoldingId(BoundWithPart boundWithPart) {
    var rtacHoldingId = new RtacHoldingId();
    rtacHoldingId.setId(fromString(boundWithPart.getItemId()));
    rtacHoldingId.setInstanceId(fromString(boundWithPart.getInstanceId()));
    rtacHoldingId.setType(ITEM);
    return rtacHoldingId;
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
