package org.folio.rtaccache.service.handler.impl;

import static org.mockito.Mockito.verify;

import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.util.CacheUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanTypeDeleteEventHandlerTest {

  private static final String LOAN_TYPES_CACHE_NAME = "loanTypesMap";

  @InjectMocks
  LoanTypeDeleteEventHandler handler;

  @Mock
  CacheUtil cacheUtil;
  @Mock
  InventoryResourceEvent resourceEvent;

  @Test
  void loanTypeDelete_shouldClearCache_whenLoanTypeIsDeleted() {
    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(LOAN_TYPES_CACHE_NAME);
  }
}

