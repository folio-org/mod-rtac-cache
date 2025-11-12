package org.folio.rtaccache.service.handler.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemDeleteEventHandlerTest {

  private static final String HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  ItemDeleteEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void itemDelete_shouldInvokeRepositoryDelete() {
    var event = new InventoryResourceEvent().type(InventoryEventType.DELETE).old(item());
    when(resourceEventUtil.getOldFromInventoryEvent(event, Item.class)).thenReturn(item());

    handler.handle(event);

    verify(holdingRepository).deleteByIdId(UUID.fromString(ITEM_ID));
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
