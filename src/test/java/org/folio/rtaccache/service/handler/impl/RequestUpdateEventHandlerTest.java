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
class RequestUpdateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  RequestUpdateEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void requestUpdate_close_shouldDecrementAndSave() {
    var rh = holdingMapped(TypeEnum.ITEM, ITEM_ID);
    rh.setTotalHoldRequests(3);
    var existingItemEntity = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.ITEM, UUID.fromString(ITEM_ID)),
      false,
      rh,
      Instant.now()
    );
    var oldReq = request(StatusEnum.OPEN_NOT_YET_FILLED, ITEM_ID);
    var newReq = request(StatusEnum.CLOSED_CANCELLED, ITEM_ID);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.UPDATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(newReq).old(oldReq));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(newReq);
    when(resourceEventUtil.getOldFromCirculationEvent(event, Request.class)).thenReturn(oldReq);
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.of(existingItemEntity));

    handler.handle(event);

    verify(holdingRepository).save(existingItemEntity);
    assert existingItemEntity.getRtacHolding().getTotalHoldRequests() == 2;
  }

  @Test
  void requestUpdate_shouldNotSave_whenWasClosedEqualsIsClosed() {
    var oldReq = request(StatusEnum.OPEN_AWAITING_PICKUP, ITEM_ID);
    var newReq = request(StatusEnum.OPEN_IN_TRANSIT, ITEM_ID); // both not closed
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.UPDATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(newReq).old(oldReq));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(newReq);
    when(resourceEventUtil.getOldFromCirculationEvent(event, Request.class)).thenReturn(oldReq);

    handler.handle(event);

    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  @Test
  void requestUpdate_shouldNotSave_whenItemIdNull() {
    var oldReq = request(StatusEnum.OPEN_NOT_YET_FILLED, ITEM_ID);
    var newReq = request(StatusEnum.CLOSED_FILLED, null);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.UPDATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(newReq).old(oldReq));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(newReq);
    when(resourceEventUtil.getOldFromCirculationEvent(event, Request.class)).thenReturn(oldReq);

    handler.handle(event);

    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  @Test
  void requestUpdate_shouldNotSave_whenItemNotFound() {
    var oldReq = request(StatusEnum.OPEN_NOT_YET_FILLED, ITEM_ID);
    var newReq = request(StatusEnum.CLOSED_UNFILLED, ITEM_ID);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.UPDATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(newReq).old(oldReq));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(newReq);
    when(resourceEventUtil.getOldFromCirculationEvent(event, Request.class)).thenReturn(oldReq);
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.empty());

    handler.handle(event);

    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  @Test
  void requestUpdate_reopen_shouldNotSave_whenClosedToOpen() {
    var rh = holdingMapped(TypeEnum.ITEM, ITEM_ID);
    rh.setTotalHoldRequests(5);
    var existingItemEntity = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.ITEM, UUID.fromString(ITEM_ID)),
      false,
      rh,
      Instant.now()
    );
    var oldReq = request(StatusEnum.CLOSED_CANCELLED, ITEM_ID);
    var newReq = request(StatusEnum.OPEN_AWAITING_DELIVERY, ITEM_ID);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.UPDATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(newReq).old(oldReq));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(newReq);
    when(resourceEventUtil.getOldFromCirculationEvent(event, Request.class)).thenReturn(oldReq);
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.of(existingItemEntity));

    handler.handle(event);

    verify(holdingRepository, never()).save(existingItemEntity);
    assert existingItemEntity.getRtacHolding().getTotalHoldRequests() == 5;
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
