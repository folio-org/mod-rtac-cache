package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.BeforeEach;
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
  RtacHoldingRepository holdingRepository;
  @Mock
  RtacHoldingMappingService mappingService;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @BeforeEach
  void setUp() {
    handler.init();
  }

  @Test
  void holdingsUpdate_shouldSaveAll() {
    var existing = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.HOLDING, UUID.fromString(HOLDINGS_ID)),
      holdingMapped(TypeEnum.HOLDING, HOLDINGS_ID),
      Instant.now()
    );
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE)._new(holdingsRecord());
    when(resourceEventUtil.getNewFromInventoryEvent(event, HoldingsRecord.class)).thenReturn(holdingsRecord());
    when(holdingRepository.findAllByHoldingsId(HOLDINGS_ID)).thenReturn(List.of(existing));
    when(mappingService.mapFrom(any(HoldingsRecord.class))).thenReturn(holdingMapped(TypeEnum.HOLDING, HOLDINGS_ID));

    handler.handle(event);

    verify(holdingRepository).saveAll(anyList());
  }

  @Test
  void holdingsUpdate_shouldNotUpdate_whenNoRtacHoldingExists() {
    var event = new InventoryResourceEvent().type(InventoryEventType.UPDATE)._new(holdingsRecord());
    when(resourceEventUtil.getNewFromInventoryEvent(event, HoldingsRecord.class)).thenReturn(holdingsRecord());
    when(holdingRepository.findAllByHoldingsId(HOLDINGS_ID)).thenReturn(Collections.emptyList());

    handler.handle(event);

    verify(holdingRepository, never()).saveAll(anyList());
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
