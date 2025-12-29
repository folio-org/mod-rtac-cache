package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemBoundWithDeleteEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  ItemBoundWithDeleteEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void itemBoundWithDelete_shouldDelete_whenItemIsBoundWith() {
    var event = setUpEventWith(boundWithPart(INSTANCE_ID, ITEM_ID));
    var existingEntity = setUpExistingItemEntity(INSTANCE_ID, ITEM_ID, true);

    handler.handle(event);

    verify(holdingRepository).deleteById(existingEntity.getId());
  }

  @Test
  void itemBoundWithDelete_shouldNotDelete_whenItemEntityNotFound() {
    var event = setUpEventWith(boundWithPart(INSTANCE_ID, ITEM_ID));
    when(holdingRepository.findById(any(RtacHoldingId.class))).thenReturn(Optional.empty());

    handler.handle(event);

    verify(holdingRepository, never()).deleteById(any(RtacHoldingId.class));
  }

  @Test
  void itemBoundWithDelete_shouldNotDelete_whenItemIsNotBoundWith() {
    var event = setUpEventWith(boundWithPart(INSTANCE_ID, ITEM_ID));
    setUpExistingItemEntity(INSTANCE_ID, ITEM_ID, false);

    handler.handle(event);

    verify(holdingRepository, never()).deleteById(any(RtacHoldingId.class));
  }

  private InventoryResourceEvent setUpEventWith(BoundWithPart part) {
    var event = new InventoryResourceEvent().type(InventoryEventType.DELETE).old(part);
    when(resourceEventUtil.getOldFromInventoryEvent(event, BoundWithPart.class)).thenReturn(part);
    return event;
  }

  private RtacHoldingEntity setUpExistingItemEntity(String instanceId, String itemId, boolean isBoundWith) {
    var id = new RtacHoldingId(UUID.fromString(instanceId), TypeEnum.ITEM, UUID.fromString(itemId));
    var holding = new RtacHolding();
    holding.setType(TypeEnum.ITEM);
    holding.setId(itemId);
    holding.setInstanceId(instanceId);
    holding.setIsBoundWith(isBoundWith);
    var entity = new RtacHoldingEntity();
    entity.setId(id);
    entity.setRtacHolding(holding);
    when(holdingRepository.findById(id)).thenReturn(Optional.of(entity));
    return entity;
  }

  private BoundWithPart boundWithPart(String instanceId, String itemId) {
    var bw = new BoundWithPart();
    bw.setInstanceId(instanceId);
    bw.setItemId(itemId);
    return bw;
  }
}
