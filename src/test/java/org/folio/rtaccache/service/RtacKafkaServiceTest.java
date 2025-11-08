package org.folio.rtaccache.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.ResourceEvent;
import org.folio.rtaccache.domain.dto.ResourceEventType;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RtacKafkaServiceTest {

  private static final String TENANT = "test";
  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  RtacKafkaService service;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  RtacHoldingMappingService mappingService;
  @Mock
  ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    service.init();
  }

  @Test
  void holdingsCreate_shouldSaveEntity() {
    var event = new ResourceEvent().type(ResourceEventType.CREATE)._new(holdingsRecord());
    when(objectMapper.convertValue(event.getNew(), HoldingsRecord.class)).thenReturn(holdingsRecord());
    when(mappingService.mapFrom(any(HoldingsRecord.class))).thenReturn(holdingMapped(TypeEnum.HOLDING, HOLDINGS_ID));

    service.handleHoldingsResourceEvent(event, TENANT);

    verify(holdingRepository).save(any(RtacHoldingEntity.class));
  }

  @Test
  void holdingsUpdate_shouldSaveAll() {
    var existing = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.HOLDING, UUID.fromString(HOLDINGS_ID)),
      holdingMapped(TypeEnum.HOLDING, HOLDINGS_ID),
      Instant.now()
    );
    var event = new ResourceEvent().type(ResourceEventType.UPDATE)._new(holdingsRecord());
    when(objectMapper.convertValue(event.getNew(), HoldingsRecord.class)).thenReturn(holdingsRecord());
    when(holdingRepository.findAllByHoldingsId(HOLDINGS_ID)).thenReturn(List.of(existing));
    when(mappingService.mapFrom(any(HoldingsRecord.class))).thenReturn(holdingMapped(TypeEnum.HOLDING, HOLDINGS_ID));

    service.handleHoldingsResourceEvent(event, TENANT);

    verify(holdingRepository).saveAll(anyList());
  }

  @Test
  void holdingsUpdate_shouldNotUpdate_whenNoRtacHoldingExists() {
    var event = new ResourceEvent().type(ResourceEventType.UPDATE)._new(holdingsRecord());
    when(objectMapper.convertValue(event.getNew(), HoldingsRecord.class)).thenReturn(holdingsRecord());
    when(holdingRepository.findAllByHoldingsId(HOLDINGS_ID)).thenReturn(Collections.emptyList());

    service.handleHoldingsResourceEvent(event, TENANT);

    verify(holdingRepository, never()).saveAll(anyList());
  }

  @Test
  void holdingsDelete_shouldInvokeRepositoryDelete() {
    var event = new ResourceEvent().type(ResourceEventType.DELETE).old(holdingsRecord());
    when(objectMapper.convertValue(event.getOld(), HoldingsRecord.class)).thenReturn(holdingsRecord());

    service.handleHoldingsResourceEvent(event, TENANT);

    verify(holdingRepository).deleteAllByHoldingsId(HOLDINGS_ID);
  }

  @Test
  void itemCreate_shouldSaveEntity() {
    var holdingsEntity = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.HOLDING, UUID.fromString(HOLDINGS_ID)),
      holdingMapped(TypeEnum.HOLDING, HOLDINGS_ID),
      Instant.now()
    );
    var event = new ResourceEvent().type(ResourceEventType.CREATE)._new(item());
    when(objectMapper.convertValue(event.getNew(), Item.class)).thenReturn(item());
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(HOLDINGS_ID), TypeEnum.HOLDING))
      .thenReturn(Optional.of(holdingsEntity));
    when(mappingService.mapForItemTypeFrom(any(RtacHolding.class), any(Item.class)))
      .thenReturn(holdingMapped(TypeEnum.ITEM, ITEM_ID));

    service.handleItemResourceEvent(event, TENANT);

    verify(holdingRepository).save(any(RtacHoldingEntity.class));
  }

  @Test
  void itemUpdate_shouldSaveEntity() {
    var itemEntity = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.ITEM, UUID.fromString(ITEM_ID)),
      holdingMapped(TypeEnum.ITEM, ITEM_ID),
      Instant.now()
    );
    var event = new ResourceEvent().type(ResourceEventType.UPDATE)._new(item());
    when(objectMapper.convertValue(event.getNew(), Item.class)).thenReturn(item());
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.of(itemEntity));
    when(mappingService.mapForItemTypeFrom(any(RtacHolding.class), any(Item.class)))
      .thenReturn(holdingMapped(TypeEnum.ITEM, ITEM_ID));

    service.handleItemResourceEvent(event, TENANT);

    verify(holdingRepository).save(itemEntity);
  }

  @Test
  void itemDelete_shouldInvokeRepositoryDelete() {
    var event = new ResourceEvent().type(ResourceEventType.DELETE).old(item());
    when(objectMapper.convertValue(event.getOld(), Item.class)).thenReturn(item());

    service.handleItemResourceEvent(event, TENANT);

    verify(holdingRepository).deleteByIdId(UUID.fromString(ITEM_ID));
  }

  private HoldingsRecord holdingsRecord() {
    var hr = new HoldingsRecord();
    hr.setId(HOLDINGS_ID);
    hr.setInstanceId(INSTANCE_ID);
    hr.setCopyNumber("HCN");
    hr.setCallNumber("CALL");
    return hr;
  }

  private Item item() {
    var it = new Item();
    it.setId(ITEM_ID);
    it.setHoldingsRecordId(HOLDINGS_ID);
    it.setBarcode("BAR");
    it.setItemLevelCallNumber("ICN");
    return it;
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

}

