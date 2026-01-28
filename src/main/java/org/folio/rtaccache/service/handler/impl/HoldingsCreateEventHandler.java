package org.folio.rtaccache.service.handler.impl;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HoldingsCreateEventHandler implements InventoryEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;


  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var holdingsData = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, HoldingsRecord.class);
    var instanceId = UUID.fromString(holdingsData.getInstanceId());
    if (holdingRepository.countByIdInstanceId(instanceId) > 0) {
      var existingRtacHoldings = holdingRepository.findAllByIdInstanceIdAndIdType(instanceId, TypeEnum.HOLDING);
      if (CollectionUtils.isEmpty(existingRtacHoldings)) {
        return;
      }
      var existingRtacHolding = existingRtacHoldings.getFirst().getRtacHolding();
      var rtacHolding = rtacHoldingMappingService.mapFrom(holdingsData);
      rtacHolding.setInstanceFormatIds(existingRtacHolding.getInstanceFormatIds());
      var rtacHoldingId = createRtacHoldingIdFromHoldings(holdingsData);
      var rtacHoldingEntity = new RtacHoldingEntity();
      rtacHoldingEntity.setId(rtacHoldingId);
      rtacHoldingEntity.setCreatedAt(Instant.now());
      rtacHoldingEntity.setRtacHolding(rtacHolding);
      holdingRepository.save(rtacHoldingEntity);
    }
  }

  private RtacHoldingId createRtacHoldingIdFromHoldings(HoldingsRecord holdingsData) {
    var rtacHoldingId = new RtacHoldingId();
    rtacHoldingId.setId(UUID.fromString(holdingsData.getId()));
    rtacHoldingId.setType(TypeEnum.HOLDING);
    rtacHoldingId.setInstanceId(UUID.fromString(holdingsData.getInstanceId()));
    return rtacHoldingId;
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.CREATE;
  }


  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.HOLDINGS;
  }
}
