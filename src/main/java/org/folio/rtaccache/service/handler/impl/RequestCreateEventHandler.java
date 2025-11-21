package org.folio.rtaccache.service.handler.impl;

import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.CirculationEntityType;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.Request;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.CirculationEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestCreateEventHandler implements CirculationEventHandler {

  private final RtacHoldingRepository holdingRepository;
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
    if (itemId == null) {
      return;
    }
    holdingRepository.findByIdIdAndIdType(UUID.fromString(itemId), TypeEnum.ITEM)
      .ifPresent(existingItemEntity -> {
        var existingRtacHolding = existingItemEntity.getRtacHolding();
        var existingRequestsCount = existingRtacHolding.getTotalHoldRequests() != null
          ? existingRtacHolding.getTotalHoldRequests() : 0;
        existingRtacHolding.setTotalHoldRequests(existingRequestsCount + 1);
        holdingRepository.save(existingItemEntity);
      });
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
