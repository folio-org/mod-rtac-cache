package org.folio.rtaccache.service.handler.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.exception.RtacKafkaUpdateException;
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
  private final RtacHoldingBulkRepository holdingBulkRepository;
  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var oldHoldingsData = resourceEventUtil.getOldFromInventoryEvent(resourceEvent, HoldingsRecord.class);
    var holdingsData = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, HoldingsRecord.class);
    log.info("Handling Holdings update event for item with id: {}", holdingsData.getId());
    var oldInstanceId = UUID.fromString(oldHoldingsData.getInstanceId());
    var newInstanceId = UUID.fromString(holdingsData.getInstanceId());
    var instanceId = oldInstanceId;
    var holdingsId = UUID.fromString(oldHoldingsData.getId());
    try {
      if (!Objects.equals(oldHoldingsData.getInstanceId(), holdingsData.getInstanceId())) {
        if (holdingRepository.countByIdInstanceId(newInstanceId) > 0) {
          var updated = holdingBulkRepository.moveHoldingsHierarchyToInstance(oldInstanceId, newInstanceId, oldHoldingsData.getId());
          if (updated == 0) {
            holdingRepository.deleteAllByIdInstanceId(newInstanceId);
            return;
          }
          instanceId = newInstanceId;
        } else {
          holdingRepository.deleteAllByHoldingsId(oldHoldingsData.getId());
          return;
        }
      }
      var holding = rtacHoldingMappingService.mapFrom(holdingsData);
      holdingBulkRepository.updateHoldingsDataFromKafkaHoldingsEvent(instanceId, holdingsId, holding);
      holdingBulkRepository.updatePieceDataFromKafkaHoldingsEvent(instanceId, oldHoldingsData.getId(), holding);
    } catch (SQLException | JsonProcessingException e) {
      log.error("Error while updating holdings data", e);
      throw new RtacKafkaUpdateException(e);
    }
    if (!Objects.equals(oldHoldingsData.getCopyNumber(), holdingsData.getCopyNumber())) {
      updateHoldingsCopyNumberForItems(holdingsData);
    }
  }

  private void updateHoldingsCopyNumberForItems(HoldingsRecord holdingsData) {
    try {
      holdingBulkRepository.updateItemsHoldingsCopyNumber(
        UUID.fromString(holdingsData.getInstanceId()),
        holdingsData.getId(), holdingsData.getCopyNumber());
    } catch (SQLException e) {
      log.error("Error occurred during bulk update of holdings copy number for instance with id: {}, holdings id: {}",
        holdingsData.getInstanceId(), holdingsData.getId(), e);
      throw new RtacKafkaUpdateException(e);
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
