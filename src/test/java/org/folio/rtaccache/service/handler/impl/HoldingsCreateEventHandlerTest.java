package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
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
class HoldingsCreateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID = UUID.randomUUID().toString();

  @InjectMocks
  HoldingsCreateEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  RtacHoldingMappingService mappingService;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void holdingsCreate_shouldSaveNewEntity_whenRecordWithTheSameInstanceIdExists() {
    var event = new InventoryResourceEvent().type(InventoryEventType.CREATE)._new(holdingsRecord());
    when(resourceEventUtil.getNewFromInventoryEvent(event, HoldingsRecord.class)).thenReturn(holdingsRecord());
    when(holdingRepository.countByIdInstanceId(UUID.fromString(INSTANCE_ID))).thenReturn(1);
    when(mappingService.mapFrom(any(HoldingsRecord.class))).thenReturn(holdingMapped(TypeEnum.HOLDING, HOLDINGS_ID));


    handler.handle(event);

    verify(holdingRepository).save(any(RtacHoldingEntity.class));
  }

  @Test
  void holdingsCreate_shouldNotSaveNewEntity_whenNoRecordWithTheSameInstanceIdExists() {
    var event = new InventoryResourceEvent().type(InventoryEventType.CREATE)._new(holdingsRecord());
    when(resourceEventUtil.getNewFromInventoryEvent(event, HoldingsRecord.class)).thenReturn(holdingsRecord());
    when(holdingRepository.countByIdInstanceId(UUID.fromString(INSTANCE_ID))).thenReturn(0);

    handler.handle(event);

    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
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
