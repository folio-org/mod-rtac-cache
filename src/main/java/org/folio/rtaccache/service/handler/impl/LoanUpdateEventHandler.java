package org.folio.rtaccache.service.handler.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.CirculationEntityType;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.Loan;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.CirculationEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanUpdateEventHandler implements CirculationEventHandler {

  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(CirculationResourceEvent resourceEvent) {
    var loanData = resourceEventUtil.getNewFromCirculationEvent(resourceEvent, Loan.class);
    var loanStatus = loanData.getStatus();
    if (loanStatus == null) {
      return;
    }
    var statusName = loanStatus.getName();
    var itemId = loanData.getItemId();
    holdingRepository.findByIdIdAndIdType(UUID.fromString(itemId), TypeEnum.ITEM)
      .ifPresent(existingItemEntity -> {
        var existingRtacHolding = existingItemEntity.getRtacHolding();

        if (loanData.getDueDate() != null && "Open".equalsIgnoreCase(statusName)) {
          existingRtacHolding.setDueDate(loanData.getDueDate());
        }

        if ("Closed".equalsIgnoreCase(statusName)) {
          existingRtacHolding.setDueDate(null);
        }

        holdingRepository.save(existingItemEntity);
      });
  }

  @Override
  public CirculationEventType getEventType() {
    return CirculationEventType.UPDATED;
  }

  @Override
  public CirculationEntityType getEntityType() {
    return CirculationEntityType.LOAN;
  }

}
