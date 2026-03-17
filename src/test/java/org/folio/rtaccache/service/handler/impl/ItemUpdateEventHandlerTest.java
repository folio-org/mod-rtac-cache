package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;
import java.util.UUID;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.kafka.KafkaItem;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
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
  RtacHoldingMappingService mappingService;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void itemUpdate_shouldSaveEntity() throws SQLException, JsonProcessingException {
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE)._new(item());
    when(resourceEventUtil.getNewFromInventoryEvent(event, KafkaItem.class)).thenReturn(item());
    when(mappingService.mapForUpdateItemFrom(any(Item.class)))
      .thenReturn(holdingMapped(TypeEnum.ITEM, ITEM_ID));
    handler.handle(event);

    verify(holdingRepository).updateItemDataFromKafkaItemEvent(
      UUID.fromString(INSTANCE_ID), UUID.fromString(ITEM_ID), holdingMapped(TypeEnum.ITEM, ITEM_ID));
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
