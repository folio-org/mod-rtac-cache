package org.folio.rtaccache.service.handler.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.exception.RtacKafkaUpdateException;
import org.folio.rtaccache.domain.kafka.KafkaItem;
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
public class ItemUpdateEventHandler implements InventoryEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingBulkRepository holdingRepository;
  private final RtacHoldingRepository rtacHoldingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var oldItem = resourceEventUtil.getOldFromInventoryEvent(resourceEvent, KafkaItem.class);
    var item = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, KafkaItem.class);
    if (item.getId() == null || item.getInstanceId() == null) {
      log.warn("Skipping item update event due to missing required identifiers, item: {}", item);
      return;
    }
    log.info("Handling item update event for item with id: {}", item.getId());

    if (isItemMoved(oldItem, item)) {
      moveItemToHolding(oldItem, item);
      return;
    }

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

  private boolean isItemMoved(KafkaItem oldItem, KafkaItem newItem) {
    if (oldItem == null) {
      return false;
    }
    return !Objects.equals(oldItem.getHoldingsRecordId(), newItem.getHoldingsRecordId());
  }

  private void moveItemToHolding(KafkaItem oldItem, KafkaItem item) {
    var instanceId = UUID.fromString(item.getInstanceId());
    var targetHoldingId = new RtacHoldingId(instanceId, TypeEnum.HOLDING, UUID.fromString(item.getHoldingsRecordId()));
    var targetHoldingEntity = rtacHoldingRepository.findById(targetHoldingId);
    if (targetHoldingEntity.isEmpty()) {
      rtacHoldingRepository.deleteByIdId(UUID.fromString(item.getId()));
      return;
    }

    var existingInstanceId = oldItem != null && oldItem.getInstanceId() != null
      ? oldItem.getInstanceId() : item.getInstanceId();
    var existingItemId = new RtacHoldingId(UUID.fromString(existingInstanceId), TypeEnum.ITEM, UUID.fromString(item.getId()));
    var existingItemEntity = rtacHoldingRepository.findById(existingItemId).orElse(null);
    var movedItem = rtacHoldingMappingService.mapForItemTypeFrom(targetHoldingEntity.get().getRtacHolding(), item);
    var movedEntity = new RtacHoldingEntity();
    movedEntity.setId(RtacHoldingId.from(movedItem));
    movedEntity.setShared(targetHoldingEntity.get().isShared());
    movedEntity.setCreatedAt(existingItemEntity != null ? existingItemEntity.getCreatedAt() : Instant.now());
    movedEntity.setRtacHolding(movedItem);
    rtacHoldingRepository.save(movedEntity);

    if (existingItemEntity != null && !existingItemEntity.getId().equals(movedEntity.getId())) {
      rtacHoldingRepository.deleteById(existingItemEntity.getId());
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

