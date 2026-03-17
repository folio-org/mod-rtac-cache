package org.folio.rtaccache.service.handler.impl;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.dto.CirculationEntityType;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.Request;
import org.folio.rtaccache.domain.dto.Request.StatusEnum;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.service.handler.CirculationEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class RequestUpdateEventHandler implements CirculationEventHandler {

  private final RtacHoldingBulkRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(CirculationResourceEvent resourceEvent) {
    var requestData = resourceEventUtil.getNewFromCirculationEvent(resourceEvent, Request.class);
    var oldRequestData = resourceEventUtil.getOldFromCirculationEvent(resourceEvent, Request.class);
    var closedStatuses = EnumSet.of(
      StatusEnum.CLOSED_UNFILLED,
      StatusEnum.CLOSED_PICKUP_EXPIRED,
      StatusEnum.CLOSED_FILLED,
      StatusEnum.CLOSED_CANCELLED
    );
    var wasClosed = closedStatuses.contains(oldRequestData.getStatus());
    var isClosed = closedStatuses.contains(requestData.getStatus());
    if (wasClosed == isClosed) {
      return;
    }
    var itemId = requestData.getItemId();
    var instanceId = requestData.getInstanceId();
    if (itemId == null || instanceId == null) {
      return;
    }

    if (isClosed) {
      try {
        holdingRepository.updateItemsHoldCount(UUID.fromString(instanceId), UUID.fromString(itemId), -1);
      } catch (SQLException e) {
        log.error("Error during decreasing hold count in RTAC holdings by item id: {}", itemId, e);
        throw new RuntimeException(e);
      }
    }

  }

  @Override
  public CirculationEventType getEventType() {
    return CirculationEventType.UPDATED;
  }

  @Override
  public CirculationEntityType getEntityType() {
    return CirculationEntityType.REQUEST;
  }
}
