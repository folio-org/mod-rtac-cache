package org.folio.rtaccache.service.handler.impl;

import static org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum.HOLDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingsCreateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID_2 = UUID.randomUUID().toString();
  private static final String INSTANCE_FORMAT_ID = UUID.randomUUID().toString();

  @InjectMocks
  HoldingsCreateEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  RtacHoldingMappingService mappingService;
  @Mock
  ResourceEventUtil resourceEventUtil;
  @Captor
  ArgumentCaptor<RtacHoldingEntity> entityCaptor;

  @Test
  void holdingsCreate_shouldSaveNewEntity_whenRecordWithTheSameInstanceIdExists() {
    var event = new InventoryResourceEvent().type(InventoryEventType.CREATE)._new(getHoldingsEventRecord());
    when(resourceEventUtil.getNewFromInventoryEvent(event, HoldingsRecord.class)).thenReturn(getHoldingsEventRecord());
    when(holdingRepository.countByIdInstanceId(any(UUID.class))).thenReturn(1);
    when(holdingRepository.findAllByIdInstanceIdAndIdType(any(UUID.class),eq(HOLDING)))
      .thenReturn(List.of(getRtacHoldingEntity(HOLDING, HOLDINGS_ID_2)));
    when(mappingService.mapFrom(any(HoldingsRecord.class))).thenReturn(getRtacHolding(HOLDING, HOLDINGS_ID));

    handler.handle(event);

    verify(holdingRepository).save(entityCaptor.capture());
    var savedEntity = entityCaptor.getValue();
    assertEquals(HOLDINGS_ID, savedEntity.getId().getId().toString());
    assertEquals(INSTANCE_ID, savedEntity.getId().getInstanceId().toString());
    assertEquals(HOLDING, savedEntity.getId().getType());
    assertEquals(List.of(INSTANCE_FORMAT_ID), savedEntity.getRtacHolding().getInstanceFormatIds());
  }

  @Test
  void holdingsCreate_shouldNotSaveNewEntity_whenNoRecordWithTheSameInstanceIdExists() {
    var event = new InventoryResourceEvent().type(InventoryEventType.CREATE)._new(getHoldingsEventRecord());
    when(resourceEventUtil.getNewFromInventoryEvent(event, HoldingsRecord.class)).thenReturn(getHoldingsEventRecord());
    when(holdingRepository.countByIdInstanceId(any(UUID.class))).thenReturn(0);

    handler.handle(event);

    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  @Test
  void holdingsCreate_shouldNotSaveNewEntity_whenExistingRtacHoldingsIsEmpty() {
    var event = new InventoryResourceEvent().type(InventoryEventType.CREATE)._new(getHoldingsEventRecord());
    when(resourceEventUtil.getNewFromInventoryEvent(event, HoldingsRecord.class)).thenReturn(getHoldingsEventRecord());
    when(holdingRepository.countByIdInstanceId(any(UUID.class))).thenReturn(1);
    when(holdingRepository.findAllByIdInstanceIdAndIdType(any(UUID.class), eq(HOLDING)))
      .thenReturn(List.of());

    handler.handle(event);

    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  private RtacHolding getRtacHolding(TypeEnum type, String holdingId) {
    var rh = new RtacHolding();
    rh.setType(type);
    rh.setId(holdingId);
    rh.setInstanceId(INSTANCE_ID);
    rh.setHoldingsId(holdingId);
    rh.setStatus("Available");
    rh.setInstanceFormatIds(List.of(INSTANCE_FORMAT_ID));
    return rh;
  }

  private RtacHoldingEntity getRtacHoldingEntity(TypeEnum type, String id) {
    var rtacHolding = getRtacHolding(type, id);
    var entity = new RtacHoldingEntity();
    entity.setRtacHolding(rtacHolding);
    return entity;
  }

  private HoldingsRecord getHoldingsEventRecord() {
    var hr = new HoldingsRecord();
    hr.setId(HOLDINGS_ID);
    hr.setInstanceId(INSTANCE_ID);
    hr.setCopyNumber("HCN");
    hr.setCallNumber("CALL");
    return hr;
  }

}
