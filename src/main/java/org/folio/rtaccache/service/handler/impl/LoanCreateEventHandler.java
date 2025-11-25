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
public class LoanCreateEventHandler implements CirculationEventHandler {

  private final RtacHoldingRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(CirculationResourceEvent resourceEvent) {
    var loanData = resourceEventUtil.getNewFromCirculationEvent(resourceEvent, Loan.class);
    var dueDate = loanData.getDueDate();
    if(dueDate == null) {
      return;
    }
    var itemId = loanData.getItemId();
    holdingRepository.findByIdIdAndIdType(UUID.fromString(itemId), TypeEnum.ITEM)
      .ifPresent(existingItemEntity -> {
        var existingRtacHolding = existingItemEntity.getRtacHolding();
        existingRtacHolding.setDueDate(dueDate);
        holdingRepository.save(existingItemEntity);
      });
  }

  @Override
  public CirculationEventType getEventType() {
    return CirculationEventType.CREATED;
  }

  @Override
  public CirculationEntityType getEntityType() {
    return CirculationEntityType.LOAN;
  }

}
