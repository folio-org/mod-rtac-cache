package org.folio.rtaccache.service.handler.impl;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import jakarta.annotation.PostConstruct;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class HoldingsUpdateEventHandler implements InventoryEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository holdingRepository;
  private final RtacHoldingBulkRepository holdingBulkRepository;
  private final ResourceEventUtil resourceEventUtil;

  private Map<TypeEnum, BiFunction<RtacHolding, HoldingsRecord, RtacHolding>> holdingsUpdateHandlers;

  @PostConstruct
  void init() {
    holdingsUpdateHandlers = Map.of(
      TypeEnum.HOLDING, this::mapForHoldingTypeFrom,
      TypeEnum.PIECE, rtacHoldingMappingService::mapForPieceTypeFrom
    );
  }

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var oldHoldingsData = resourceEventUtil.getOldFromInventoryEvent(resourceEvent, HoldingsRecord.class);
    var holdingsData = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, HoldingsRecord.class);
    log.info("Handling Holdings update event for item with id: {}", holdingsData.getId());
    var instanceId = UUID.fromString(oldHoldingsData.getInstanceId());
    var updatedEntities = holdingRepository.findAllByInstanceIdAndHoldingsIdAndTypeIn(instanceId, holdingsData.getId(),
        List.of(TypeEnum.HOLDING.name(), TypeEnum.PIECE.name()))
      .stream()
      .map(entity -> {
        var existingRtacHolding = entity.getRtacHolding();
        var handler = holdingsUpdateHandlers.get(existingRtacHolding.getType());
        entity.setRtacHolding(handler.apply(existingRtacHolding, holdingsData));
        return entity;
      })
      .toList();

    if (isNotEmpty(updatedEntities)) {
      holdingRepository.saveAll(updatedEntities);
    }
    if (!Objects.equals(oldHoldingsData.getCopyNumber(), holdingsData.getCopyNumber())) {
      updateHoldingsCopyNumber(holdingsData);
    }
  }

  private RtacHolding mapForHoldingTypeFrom(RtacHolding rtacHolding, HoldingsRecord holdings) {
    return rtacHoldingMappingService.mapFrom(holdings);
  }

  private void updateHoldingsCopyNumber(HoldingsRecord holdingsData) {
    try {
      holdingBulkRepository.bulkUpdateHoldingsCopyNumberByInstanceId(
        UUID.fromString(holdingsData.getInstanceId()),
        holdingsData.getId(), holdingsData.getCopyNumber());
    } catch (SQLException e) {
      log.error("Error occurred during bulk update of holdings copy number for instance with id: {}, holdings id: {}",
        holdingsData.getInstanceId(), holdingsData.getId(), e);
    }
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.UPDATE;
  }


  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.HOLDINGS;
  }

}
