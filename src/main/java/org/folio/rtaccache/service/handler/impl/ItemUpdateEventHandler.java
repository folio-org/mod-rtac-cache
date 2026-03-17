package org.folio.rtaccache.service.handler.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.exception.RtacKafkaUpdateException;
import org.folio.rtaccache.domain.kafka.KafkaItem;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class ItemUpdateEventHandler implements InventoryEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingBulkRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var item = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, KafkaItem.class);
    log.info("Handling item update event for item with id: {}", item.getId());
    var rtacHoldingUpdate = rtacHoldingMappingService.mapForUpdateItemFrom(item);
    try {
      holdingRepository.updateItemDataFromKafkaItemEvent(UUID.fromString(item.getInstanceId()),
        UUID.fromString(item.getId()),
        rtacHoldingUpdate);
    } catch (SQLException e) {
      log.error("Error during updating RTAC holdings with item data by item id: {}", item.getId(), e);
      throw new RtacKafkaUpdateException(e);
    } catch (JsonProcessingException e) {
      log.error("Error during mapping item data to JSON for item with id: {}", item.getId(), e);
      throw new RtacKafkaUpdateException(e);
    }
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

