package org.folio.rtaccache.service.handler.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.rtaccache.domain.dto.BoundWithPart;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemBoundWithCreateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String NEW_HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  ItemBoundWithCreateEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void itemBoundWithCreate_shouldClearCache_whenItemBounded() {
    var event = setUpEventWith(boundWithPart(INSTANCE_ID, NEW_HOLDINGS_ID, ITEM_ID));

    handler.handle(event);

    verify(holdingRepository).deleteAllByIdInstanceId(UUID.fromString(INSTANCE_ID));
  }

  @Test
  void itemBoundWithCreate_shouldDoNothing_whenInstanceIdIsNull() {
    var event = new InventoryResourceEvent()
      .type(InventoryEventType.CREATE)
      ._new(boundWithPart(null, NEW_HOLDINGS_ID, ITEM_ID));
    when(resourceEventUtil.getNewFromInventoryEvent(event, BoundWithPart.class))
      .thenReturn(boundWithPart(null, NEW_HOLDINGS_ID, ITEM_ID));

    handler.handle(event);

    verify(holdingRepository, never()).deleteAllByIdInstanceId(UUID.fromString(INSTANCE_ID));
  }

  private InventoryResourceEvent setUpEventWith(BoundWithPart part) {
    var event = new InventoryResourceEvent().type(InventoryEventType.CREATE)._new(part);
    when(resourceEventUtil.getNewFromInventoryEvent(event, BoundWithPart.class)).thenReturn(part);
    return event;
  }

  private BoundWithPart boundWithPart(String instanceId, String holdingsId, String itemId) {
    var bw = new BoundWithPart();
    bw.setInstanceId(instanceId);
    bw.setHoldingsRecordId(holdingsId);
    bw.setItemId(itemId);
    return bw;
  }

}
