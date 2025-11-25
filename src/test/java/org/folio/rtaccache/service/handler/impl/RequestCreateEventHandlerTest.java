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
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.Request;
import org.folio.rtaccache.domain.dto.Request.StatusEnum;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestCreateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  RequestCreateEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void requestCreate_shouldIncrementAndSave_whenStatusOpen() {
    var rh = holdingMapped(TypeEnum.ITEM, ITEM_ID);
    rh.setTotalHoldRequests(2);
    var existingItemEntity = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.ITEM, UUID.fromString(ITEM_ID)),
      rh,
      Instant.now()
    );
    var request = request(StatusEnum.OPEN_NOT_YET_FILLED, ITEM_ID);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.CREATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(request));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(request);
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.of(existingItemEntity));

    handler.handle(event);

    verify(holdingRepository).save(existingItemEntity);
    assert existingItemEntity.getRtacHolding().getTotalHoldRequests() == 3;
  }

  @Test
  void requestCreate_shouldNotSave_whenStatusNotOpen() {
    var request = request(StatusEnum.CLOSED_FILLED, ITEM_ID);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.CREATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(request));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(request);

    handler.handle(event);    handler.handle(event);

    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  @Test
  void requestCreate_shouldNotSave_whenItemIdNull() {
    var request = request(StatusEnum.OPEN_AWAITING_PICKUP, null);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.CREATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(request));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(request);

    handler.handle(event);

    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  @Test
  void requestCreate_shouldNotSave_whenItemNotFound() {
    var request = request(StatusEnum.OPEN_IN_TRANSIT, ITEM_ID);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.CREATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(request));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(request);
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.empty());

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

  private Request request(StatusEnum status, String itemId) {
    var req = new Request();
    req.setStatus(status);
    req.setItemId(itemId);
    return req;
  }


}
