package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.kafka.KafkaItem;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemUpdateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  ItemUpdateEventHandler handler;

  @Mock
  RtacHoldingBulkRepository holdingRepository;
  @Mock
  RtacHoldingRepository rtacHoldingRepository;
  @Mock
  RtacHoldingMappingService mappingService;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void itemUpdate_shouldSaveEntity() throws SQLException, JsonProcessingException {
    var oldItem = item();
    var newItem = item();
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE).old(oldItem)._new(newItem);
    when(resourceEventUtil.getOldFromInventoryEvent(event, KafkaItem.class)).thenReturn(oldItem);
    when(resourceEventUtil.getNewFromInventoryEvent(event, KafkaItem.class)).thenReturn(newItem);
    when(mappingService.mapForUpdateItemFrom(any(Item.class)))
      .thenReturn(holdingMapped(TypeEnum.ITEM, ITEM_ID));
    handler.handle(event);

    verify(holdingRepository).updateItemDataFromKafkaItemEvent(
      UUID.fromString(INSTANCE_ID), UUID.fromString(ITEM_ID), holdingMapped(TypeEnum.ITEM, ITEM_ID));
  }

  @Test
  void itemUpdate_shouldMoveItemToAnotherInstanceAndHoldingWhenTargetIsCached() throws SQLException, JsonProcessingException {
    var oldItem = item();
    var newItem = item();
    var newInstanceId = UUID.randomUUID().toString();
    var newHoldingsId = UUID.randomUUID().toString();
    newItem.setInstanceId(newInstanceId);
    newItem.setHoldingsRecordId(newHoldingsId);
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE).old(oldItem)._new(newItem);

    var targetHolding = holdingMapped(TypeEnum.HOLDING, newHoldingsId);
    targetHolding.setInstanceId(newInstanceId);
    targetHolding.setHoldingsId(newHoldingsId);
    var targetHoldingEntity = new RtacHoldingEntity();
    targetHoldingEntity.setId(new RtacHoldingId(UUID.fromString(newInstanceId), TypeEnum.HOLDING, UUID.fromString(newHoldingsId)));
    targetHoldingEntity.setRtacHolding(targetHolding);
    targetHoldingEntity.setShared(true);

    var existingItemEntity = new RtacHoldingEntity();
    existingItemEntity.setId(new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.ITEM, UUID.fromString(ITEM_ID)));
    existingItemEntity.setCreatedAt(Instant.now());

    when(resourceEventUtil.getOldFromInventoryEvent(event, KafkaItem.class)).thenReturn(oldItem);
    when(resourceEventUtil.getNewFromInventoryEvent(event, KafkaItem.class)).thenReturn(newItem);
    when(rtacHoldingRepository.findById(new RtacHoldingId(UUID.fromString(newInstanceId), TypeEnum.HOLDING, UUID.fromString(newHoldingsId))))
      .thenReturn(java.util.Optional.of(targetHoldingEntity));
    when(rtacHoldingRepository.findById(new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.ITEM, UUID.fromString(ITEM_ID))))
      .thenReturn(java.util.Optional.of(existingItemEntity));
    var movedItemHolding = holdingMapped(TypeEnum.ITEM, ITEM_ID);
    movedItemHolding.setInstanceId(newInstanceId);
    movedItemHolding.setHoldingsId(newHoldingsId);
    when(mappingService.mapForItemTypeFrom(targetHolding, newItem)).thenReturn(movedItemHolding);

    handler.handle(event);

    verify(rtacHoldingRepository).save(argThat(saved ->
      saved.getId().getInstanceId().equals(UUID.fromString(newInstanceId))
        && saved.getId().getId().equals(UUID.fromString(ITEM_ID))));
    verify(rtacHoldingRepository).deleteById(existingItemEntity.getId());
    verify(holdingRepository, never()).updateItemDataFromKafkaItemEvent(any(), any(), any());
  }

  @Test
  void itemUpdate_shouldNotMoveWhenOnlyInstanceIdChanged() throws SQLException, JsonProcessingException {
    var oldItem = item();
    var newItem = item();
    var newInstanceId = UUID.randomUUID().toString();
    newItem.setInstanceId(newInstanceId);
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE).old(oldItem)._new(newItem);

    when(resourceEventUtil.getOldFromInventoryEvent(event, KafkaItem.class)).thenReturn(oldItem);
    when(resourceEventUtil.getNewFromInventoryEvent(event, KafkaItem.class)).thenReturn(newItem);
    var mappedItemHolding = holdingMapped(TypeEnum.ITEM, ITEM_ID);
    when(mappingService.mapForUpdateItemFrom(any(Item.class))).thenReturn(mappedItemHolding);

    handler.handle(event);

    verify(holdingRepository).updateItemDataFromKafkaItemEvent(
      UUID.fromString(newInstanceId), UUID.fromString(ITEM_ID), mappedItemHolding);
  }

  @Test
  void itemUpdate_shouldDeleteItemWhenTargetHoldingIsNotFound() throws SQLException, JsonProcessingException {
    var oldItem = item();
    var newItem = item();
    var newInstanceId = UUID.randomUUID().toString();
    var newHoldingsId = UUID.randomUUID().toString();
    newItem.setInstanceId(newInstanceId);
    newItem.setHoldingsRecordId(newHoldingsId);
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE).old(oldItem)._new(newItem);

    when(resourceEventUtil.getOldFromInventoryEvent(event, KafkaItem.class)).thenReturn(oldItem);
    when(resourceEventUtil.getNewFromInventoryEvent(event, KafkaItem.class)).thenReturn(newItem);
    when(rtacHoldingRepository.findById(new RtacHoldingId(UUID.fromString(newInstanceId), TypeEnum.HOLDING, UUID.fromString(newHoldingsId))))
      .thenReturn(java.util.Optional.empty());

    handler.handle(event);

    verify(rtacHoldingRepository).deleteByIdId(UUID.fromString(ITEM_ID));
    verify(holdingRepository, never()).updateItemDataFromKafkaItemEvent(any(), any(), any());
  }

  @Test
  void itemUpdate_shouldMoveItemToAnotherHoldingWithinSameInstance()
    throws SQLException, JsonProcessingException {
    var oldItem = item();
    var newItem = item();
    var newHoldingsId = UUID.randomUUID().toString();
    newItem.setHoldingsRecordId(newHoldingsId);
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE).old(oldItem)._new(newItem);

    var targetHolding = holdingMapped(TypeEnum.HOLDING, newHoldingsId);
    targetHolding.setInstanceId(INSTANCE_ID);
    targetHolding.setHoldingsId(newHoldingsId);
    var targetHoldingEntity = new RtacHoldingEntity();
    targetHoldingEntity.setId(new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.HOLDING, UUID.fromString(newHoldingsId)));
    targetHoldingEntity.setRtacHolding(targetHolding);
    targetHoldingEntity.setShared(true);

    var existingItemEntity = new RtacHoldingEntity();
    existingItemEntity.setId(new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.ITEM, UUID.fromString(ITEM_ID)));
    existingItemEntity.setCreatedAt(Instant.now());

    var mappedItemHolding = holdingMapped(TypeEnum.ITEM, ITEM_ID);
    mappedItemHolding.setHoldingsId(newHoldingsId);

    when(resourceEventUtil.getOldFromInventoryEvent(event, KafkaItem.class)).thenReturn(oldItem);
    when(resourceEventUtil.getNewFromInventoryEvent(event, KafkaItem.class)).thenReturn(newItem);
    when(rtacHoldingRepository.findById(new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.HOLDING, UUID.fromString(newHoldingsId))))
      .thenReturn(java.util.Optional.of(targetHoldingEntity));
    when(rtacHoldingRepository.findById(new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.ITEM, UUID.fromString(ITEM_ID))))
      .thenReturn(java.util.Optional.of(existingItemEntity));
    when(mappingService.mapForItemTypeFrom(targetHolding, newItem)).thenReturn(mappedItemHolding);

    handler.handle(event);

    verify(rtacHoldingRepository).save(argThat(saved ->
      saved.getId().getInstanceId().equals(UUID.fromString(INSTANCE_ID))
        && newHoldingsId.equals(saved.getRtacHolding().getHoldingsId())));
    verify(rtacHoldingRepository, never()).deleteById(any());
    verify(holdingRepository, never()).updateItemDataFromKafkaItemEvent(any(), any(), any());
  }

  private RtacHolding holdingMapped(TypeEnum type, String id) {
    var rh = new RtacHolding();
    rh.setType(type);
    rh.setId(id);
    rh.setInstanceId(INSTANCE_ID);
    rh.setHoldingsId(HOLDINGS_ID);
    rh.setStatus("Available");
    return rh;
  }

  private KafkaItem item() {
    var it = new KafkaItem();
    it.setId(ITEM_ID);
    it.setInstanceId(INSTANCE_ID);
    it.setHoldingsRecordId(HOLDINGS_ID);
    it.setBarcode("BAR");
    it.setItemLevelCallNumber("ICN");
    return it;
  }

}
