package org.folio.rtaccache.service.handler.impl;


import java.sql.SQLException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.dto.CirculationEntityType;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.Loan;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.service.handler.CirculationEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class LoanCreateEventHandler implements CirculationEventHandler {

  private final RtacHoldingBulkRepository holdingRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(CirculationResourceEvent resourceEvent) {
    var loanData = resourceEventUtil.getNewFromCirculationEvent(resourceEvent, Loan.class);
    var dueDate = loanData.getDueDate();
    if(dueDate == null) {
      return;
    }
    var itemId = UUID.fromString(loanData.getItemId());
    try {
      holdingRepository.updateItemsDueDate(itemId, dueDate);
    } catch (SQLException e) {
      log.error("Error during updating RTAC holdings with due date by item id: {}", itemId, e);
      throw new RuntimeException(e);
    }
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
