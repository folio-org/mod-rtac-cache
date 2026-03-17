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
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.service.handler.CirculationEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class RequestCreateEventHandler implements CirculationEventHandler {

  private final RtacHoldingBulkRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(CirculationResourceEvent resourceEvent) {
    var requestData = resourceEventUtil.getNewFromCirculationEvent(resourceEvent, Request.class);
    var openStatuses = EnumSet.of(
      Request.StatusEnum.OPEN_AWAITING_DELIVERY,
      Request.StatusEnum.OPEN_IN_TRANSIT,
      Request.StatusEnum.OPEN_NOT_YET_FILLED,
      Request.StatusEnum.OPEN_AWAITING_PICKUP
    );
    if (!openStatuses.contains(requestData.getStatus())) {
      return;
    }
    var itemId = requestData.getItemId();
    var instanceId = requestData.getInstanceId();
    if (itemId == null || instanceId == null) {
      return;
    }

    try {
      holdingRepository.updateItemsHoldCount(UUID.fromString(instanceId), UUID.fromString(itemId), 1);
    } catch (SQLException e) {
      log.error("Error during updating RTAC holdings with incremented hold count by item id: {}", itemId, e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public CirculationEventType getEventType() {
    return CirculationEventType.CREATED;
  }

  @Override
  public CirculationEntityType getEntityType() {
    return CirculationEntityType.REQUEST;
  }
}
