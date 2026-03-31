package org.folio.rtaccache.service.handler.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.LoanType;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.util.CacheUtil;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanTypeUpdateEventHandlerTest {

  private static final String LOAN_TYPES_CACHE_NAME = "loanTypesMap";

  @InjectMocks
  LoanTypeUpdateEventHandler handler;

  @Mock
  CacheUtil cacheUtil;
  @Mock
  InventoryResourceEvent resourceEvent;
  @Mock
  RtacHoldingBulkRepository rtacHoldingBulkRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void loanTypeUpdate_shouldUpdateRtacHoldings_whenNameChanged() throws SQLException {
    var oldLoanType = new LoanType().id("l-id").name("old");
    var newLoanType = new LoanType().id("l-id").name("new");
    when(resourceEventUtil.getOldFromInventoryEvent(resourceEvent, LoanType.class)).thenReturn(oldLoanType);
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, LoanType.class)).thenReturn(newLoanType);

    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(LOAN_TYPES_CACHE_NAME);
    verify(rtacHoldingBulkRepository).bulkUpdateLoanTypeData(oldLoanType, newLoanType);
  }

  @Test
  void loanTypeUpdate_shouldSkipRtacUpdate_whenNameNotChanged() throws SQLException {
    var oldLoanType = new LoanType().id("l-id").name("same");
    var newLoanType = new LoanType().id("l-id").name("same");
    when(resourceEventUtil.getOldFromInventoryEvent(resourceEvent, LoanType.class)).thenReturn(oldLoanType);
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, LoanType.class)).thenReturn(newLoanType);

    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(LOAN_TYPES_CACHE_NAME);
    verify(rtacHoldingBulkRepository, never()).bulkUpdateLoanTypeData(oldLoanType, newLoanType);
  }
}

