package org.folio.rtaccache.service.handler.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.BoundWithPart;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemBoundWithCreateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String OLD_HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String NEW_HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  ItemBoundWithCreateEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  RtacHoldingMappingService mappingService;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void itemBoundWithCreate_shouldSaveEntity_whenHoldingsChangedAndHoldingsExists() {
    var event = setUpEventWith(boundWithPart(INSTANCE_ID, NEW_HOLDINGS_ID, ITEM_ID));
    var existingItemEntity = setUpExistingItemEntity(INSTANCE_ID, ITEM_ID, OLD_HOLDINGS_ID);
    var existingHoldingsEntity = setUpExistingHoldingsEntity(INSTANCE_ID, NEW_HOLDINGS_ID, true);
    setUpMappingServiceForBoundWith(ITEM_ID, INSTANCE_ID, NEW_HOLDINGS_ID);

    handler.handle(event);

    var captor = ArgumentCaptor.forClass(RtacHoldingEntity.class);
    verify(holdingRepository).save(captor.capture());
    var saved = captor.getValue();
    assertEquals(UUID.fromString(ITEM_ID), saved.getId().getId());
    assertEquals(UUID.fromString(INSTANCE_ID), saved.getId().getInstanceId());
    assertEquals(TypeEnum.ITEM, saved.getId().getType());
    assertEquals(existingItemEntity.getCreatedAt(), saved.getCreatedAt());
    assertEquals(existingHoldingsEntity.isShared(), saved.isShared()); // verifies setShared(existingHoldingsEntity.isShared())
  }

  @Test
  void itemBoundWithCreate_shouldNotSave_whenItemEntityNotFound() {
    var event = new InventoryResourceEvent()
      .type(InventoryEventType.CREATE)
      ._new(boundWithPart(INSTANCE_ID, NEW_HOLDINGS_ID, ITEM_ID));
    when(resourceEventUtil.getNewFromInventoryEvent(event, BoundWithPart.class))
      .thenReturn(boundWithPart(INSTANCE_ID, NEW_HOLDINGS_ID, ITEM_ID));

    handler.handle(event);

    verify(holdingRepository, never()).findByIdIdAndIdType(any(UUID.class), eq(TypeEnum.HOLDING));
    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  @Test
  void itemBoundWithCreate_shouldNotSave_whenHoldingsIdIsSame() {
    var event = new InventoryResourceEvent()
      .type(InventoryEventType.CREATE)
      ._new(boundWithPart(INSTANCE_ID, OLD_HOLDINGS_ID, ITEM_ID));
    when(resourceEventUtil.getNewFromInventoryEvent(event, BoundWithPart.class))
      .thenReturn(boundWithPart(INSTANCE_ID, OLD_HOLDINGS_ID, ITEM_ID));
    setUpExistingItemEntity(INSTANCE_ID, ITEM_ID, OLD_HOLDINGS_ID);

    handler.handle(event);

    verify(holdingRepository, never()).findByIdIdAndIdType(any(UUID.class), eq(TypeEnum.HOLDING));
    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  private InventoryResourceEvent setUpEventWith(BoundWithPart part) {
    var event = new InventoryResourceEvent().type(InventoryEventType.CREATE)._new(part);
    when(resourceEventUtil.getNewFromInventoryEvent(event, BoundWithPart.class)).thenReturn(part);
    return event;
  }

  private RtacHoldingEntity setUpExistingItemEntity(String instanceId, String itemId, String holdingsId) {
    var entity = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(instanceId), TypeEnum.ITEM, UUID.fromString(itemId)),
      false,
      holdingMapped(TypeEnum.ITEM, itemId, instanceId, holdingsId),
      Instant.now()
    );
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(itemId), TypeEnum.ITEM)).thenReturn(Optional.of(entity));
    return entity;
  }

  private RtacHoldingEntity setUpExistingHoldingsEntity(String instanceId, String holdingsId, boolean shared) {
    var entity = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(instanceId), TypeEnum.HOLDING, UUID.fromString(holdingsId)),
      shared,
      holdingMapped(TypeEnum.HOLDING, holdingsId, instanceId, holdingsId),
      Instant.now()
    );
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(holdingsId), TypeEnum.HOLDING)).thenReturn(Optional.of(entity));
    return entity;
  }

  private void setUpMappingServiceForBoundWith(String itemId, String instanceId, String newHoldingsId) {
    var mappedBoundWithItem = holdingMapped(TypeEnum.ITEM, itemId, instanceId, newHoldingsId);
    when(mappingService.mapForBoundWithItemTypeFrom(any(RtacHolding.class), any(RtacHolding.class)))
      .thenReturn(mappedBoundWithItem);
  }

  private BoundWithPart boundWithPart(String instanceId, String holdingsId, String itemId) {
    var bw = new BoundWithPart();
    bw.setInstanceId(instanceId);
    bw.setHoldingsRecordId(holdingsId);
    bw.setItemId(itemId);
    return bw;
  }

  private RtacHolding holdingMapped(TypeEnum type, String id, String instanceId, String holdingsId) {
    var rh = new RtacHolding();
    rh.setType(type);
    rh.setId(id);
    rh.setInstanceId(instanceId);
    rh.setHoldingsId(holdingsId);
    rh.setStatus("Available");
    return rh;
  }
}
