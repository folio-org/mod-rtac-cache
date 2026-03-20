package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.UUID;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.Request;
import org.folio.rtaccache.domain.dto.Request.StatusEnum;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestCreateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  RequestCreateEventHandler handler;

  @Mock
  RtacHoldingBulkRepository holdingRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void requestCreate_shouldIncrementAndSave_whenStatusOpen() throws SQLException {
    var request = request(StatusEnum.OPEN_NOT_YET_FILLED, ITEM_ID);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.CREATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(request));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(request);

    handler.handle(event);

    verify(holdingRepository).updateItemsHoldCount(UUID.fromString(INSTANCE_ID), UUID.fromString(ITEM_ID), 1);
  }

  @Test
  void requestCreate_shouldNotSave_whenStatusNotOpen() throws SQLException {
    var request = request(StatusEnum.CLOSED_FILLED, ITEM_ID);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.CREATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(request));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(request);

    handler.handle(event);    handler.handle(event);

    verify(holdingRepository, never()).updateItemsHoldCount(any(), any(), anyInt());
  }

  @Test
  void requestCreate_shouldNotSave_whenItemIdNull() throws SQLException {
    var request = request(StatusEnum.OPEN_AWAITING_PICKUP, null);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.CREATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(request));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(request);

    handler.handle(event);

    verify(holdingRepository, never()).updateItemsHoldCount(any(), any(), anyInt());
  }

  @Test
  void requestCreate_shouldNotSave_whenInstanceIdIdNull() throws SQLException {
    var request = request(StatusEnum.OPEN_AWAITING_PICKUP, ITEM_ID);
    request.setInstanceId(null);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.CREATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(request));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Request.class)).thenReturn(request);

    handler.handle(event);

    verify(holdingRepository, never()).updateItemsHoldCount(any(), any(), anyInt());
  }

  private Request request(StatusEnum status, String itemId) {
    var req = new Request();
    req.setStatus(status);
    req.setInstanceId(INSTANCE_ID);
    req.setItemId(itemId);
    return req;
  }
}
