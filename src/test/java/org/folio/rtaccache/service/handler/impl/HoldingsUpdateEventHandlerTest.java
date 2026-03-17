package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;
import java.util.UUID;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingsUpdateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID = UUID.randomUUID().toString();

  @InjectMocks
  HoldingsUpdateEventHandler handler;

  @Mock
  RtacHoldingBulkRepository holdingBulkRepository;
  @Mock
  RtacHoldingMappingService mappingService;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void holdingsUpdate_shouldSaveAll() throws SQLException, JsonProcessingException {
    var oldRecord = holdingsRecord();
    var newRecord = holdingsRecord();
    newRecord.setCopyNumber("HCN2");
    var mappedHolding = holdingMapped(TypeEnum.HOLDING, HOLDINGS_ID);
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE).old(oldRecord)._new(newRecord);
    when(resourceEventUtil.getOldFromInventoryEvent(event, HoldingsRecord.class)).thenReturn(oldRecord);
    when(resourceEventUtil.getNewFromInventoryEvent(event, HoldingsRecord.class)).thenReturn(newRecord);
    when(mappingService.mapFrom(any(HoldingsRecord.class))).thenReturn(mappedHolding);

    handler.handle(event);

    verify(holdingBulkRepository).updateItemsHoldingsCopyNumber(UUID.fromString(INSTANCE_ID), HOLDINGS_ID, "HCN2");
    verify(holdingBulkRepository).updateHoldingsDataFromKafkaHoldingsEvent(UUID.fromString(INSTANCE_ID), UUID.fromString(HOLDINGS_ID), mappedHolding);
    verify(holdingBulkRepository).updatePieceDataFromKafkaHoldingsEvent(UUID.fromString(INSTANCE_ID), HOLDINGS_ID, mappedHolding);
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

  private HoldingsRecord holdingsRecord() {
    var hr = new HoldingsRecord();
    hr.setId(HOLDINGS_ID);
    hr.setInstanceId(INSTANCE_ID);
    hr.setCopyNumber("HCN");
    hr.setCallNumber("CALL");
    return hr;
  }

}
