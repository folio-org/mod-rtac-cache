package org.folio.rtaccache.service.handler.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.Loan;
import org.folio.rtaccache.domain.dto.LoanStatus;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanCreateEventHandlerTest {

  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  LoanCreateEventHandler handler;

  @Mock
  RtacHoldingBulkRepository holdingRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void loanCreate_shouldSetDueDateAndSave() throws SQLException {
    var loan = loan(new Date(), "Open");
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.CREATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(loan));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Loan.class)).thenReturn(loan);

    handler.handle(event);

    verify(holdingRepository).updateItemsDueDate(UUID.fromString(ITEM_ID), loan.getDueDate());
  }

  private Loan loan(Date dueDate, String statusName) {
    var loan = new Loan();
    loan.setItemId(ITEM_ID);
    loan.setDueDate(dueDate);
    if (statusName != null) {
      var ls = new LoanStatus();
      ls.setName(statusName);
      loan.setStatus(ls);
    }
    return loan;
  }

}
