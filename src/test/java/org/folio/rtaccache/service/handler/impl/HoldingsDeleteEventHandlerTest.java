package org.folio.rtaccache.service.handler.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingsDeleteEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID = UUID.randomUUID().toString();

  @InjectMocks
  HoldingsDeleteEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  RtacHoldingMappingService mappingService;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void holdingsDelete_shouldInvokeRepositoryDelete() {
    var event = new InventoryResourceEvent().type(InventoryEventType.DELETE).old(holdingsRecord());
    when(resourceEventUtil.getOldFromInventoryEvent(event, HoldingsRecord.class)).thenReturn(holdingsRecord());

    handler.handle(event);

    verify(holdingRepository).deleteAllByHoldingsId(HOLDINGS_ID);
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
