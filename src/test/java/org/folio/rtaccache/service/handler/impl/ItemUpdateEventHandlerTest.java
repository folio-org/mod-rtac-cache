package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
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
  RtacHoldingRepository holdingRepository;
  @Mock
  RtacHoldingMappingService mappingService;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void itemUpdate_shouldSaveEntity() {
    var itemEntity = getRtacEntity(TypeEnum.ITEM, ITEM_ID);
    var holdingsEntity = getRtacEntity(TypeEnum.HOLDING, HOLDINGS_ID);
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE)._new(item());
    when(resourceEventUtil.getNewFromInventoryEvent(event, Item.class)).thenReturn(item());
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.of(itemEntity));
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(HOLDINGS_ID), TypeEnum.HOLDING))
      .thenReturn(Optional.of(holdingsEntity));
    when(mappingService.mapForItemTypeFrom(any(RtacHolding.class), any(Item.class)))
      .thenReturn(holdingMapped(TypeEnum.ITEM, ITEM_ID));

    handler.handle(event);

    verify(holdingRepository).save(itemEntity);
  }

  @Test
  void itemUpdate_shouldNotUpdateEntity_whenItemNotFound() {
    var itemEntity = getRtacEntity(TypeEnum.ITEM, ITEM_ID);
    var holdingsEntity = getRtacEntity(TypeEnum.HOLDING, HOLDINGS_ID);
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE)._new(item());
    when(resourceEventUtil.getNewFromInventoryEvent(event, Item.class)).thenReturn(item());
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.of(itemEntity));
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(HOLDINGS_ID), TypeEnum.HOLDING))
      .thenReturn(Optional.empty());

    handler.handle(event);

    verify(holdingRepository, never()).save(itemEntity);
  }

  @Test
  void itemUpdate_shouldNotUpdateEntity_whenHoldingsNotFound() {
    var itemEntity = getRtacEntity(TypeEnum.ITEM, ITEM_ID);
    var holdingsEntity = getRtacEntity(TypeEnum.HOLDING, HOLDINGS_ID);
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE)._new(item());
    when(resourceEventUtil.getNewFromInventoryEvent(event, Item.class)).thenReturn(item());
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.empty());;

    handler.handle(event);

    verify(holdingRepository, never()).save(itemEntity);
  }

  private RtacHoldingEntity getRtacEntity(TypeEnum item, String itemId) {
    return new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), item, UUID.fromString(itemId)),
      false,
      holdingMapped(item, itemId),
      Instant.now()
    );
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

  private Item item() {
    var it = new Item();
    it.setId(ITEM_ID);
    it.setHoldingsRecordId(HOLDINGS_ID);
    it.setBarcode("BAR");
    it.setItemLevelCallNumber("ICN");
    return it;
  }

}
