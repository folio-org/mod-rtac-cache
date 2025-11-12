package org.folio.rtaccache.service.handler.impl;

import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.CirculationEntityType;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.Request;
import org.folio.rtaccache.domain.dto.Request.StatusEnum;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.CirculationEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestUpdateEventHandler implements CirculationEventHandler {

  private final RtacHoldingRepository holdingRepository;
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
    if (itemId == null) {
      return;
    }
    holdingRepository.findByIdIdAndIdType(UUID.fromString(itemId), TypeEnum.ITEM)
      .ifPresent(existingItemEntity -> {
        var existingRtacHolding = existingItemEntity.getRtacHolding();
        var existingTotalRequestsCount = existingRtacHolding.getTotalHoldRequests();
        if (existingTotalRequestsCount == null) {
          return;
        }
        if (isClosed && existingTotalRequestsCount > 0) {
          existingRtacHolding.setTotalHoldRequests(existingTotalRequestsCount - 1);
          holdingRepository.save(existingItemEntity);
        }
      });
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
