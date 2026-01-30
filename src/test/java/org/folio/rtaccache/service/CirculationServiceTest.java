package org.folio.rtaccache.service;

import static org.folio.rtaccache.TestUtil.queryContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.folio.rtaccache.client.CirculationClient;
import org.folio.rtaccache.client.SettingsClient;
import org.folio.rtaccache.domain.dto.Loan;
import org.folio.rtaccache.domain.dto.Loans;
import org.folio.rtaccache.domain.dto.Request;
import org.folio.rtaccache.domain.dto.Request.StatusEnum;
import org.folio.rtaccache.domain.dto.Requests;
import org.folio.rtaccache.domain.dto.Settings;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;

@ExtendWith(MockitoExtension.class)
class CirculationServiceTest {

  @Mock
  private CirculationClient circulationClient;
  @Mock
  private SettingsClient settingsClient;
  @Mock
  private SystemUserScopedExecutionService systemUserScopedExecutionService;
  @Spy
  private AsyncTaskExecutor asyncTaskExecutor = new VirtualThreadTaskExecutor();
  @InjectMocks
  private CirculationService circulationService;

  @Test
  void getLoanDueDatesForItems_shouldFetchLoansInParallel() {
    when(settingsClient.getSettings(any()))
      .thenReturn(new Settings().items(List.of()));

    List<String> itemIds = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      itemIds.add("id_" + i);
    }
    var loan = new Loan();
    loan.setItemId("id_1");
    loan.setDueDate(Date.from(Instant.now()));
    var loan2 = new Loan();
    loan2.setItemId("id_99");
    loan2.setDueDate(Date.from(Instant.now()));
    when(circulationClient.getLoans(argThat(queryContains("id_1"))))
      .thenReturn(new Loans()
        .loans(List.of(loan))
        .totalRecords(1));
    when(circulationClient.getLoans(argThat(queryContains("id_99"))))
      .thenReturn(new Loans()
        .loans(List.of(loan2))
        .totalRecords(1));

    var response = circulationService.getLoanDueDatesForItems(itemIds);

    verify(circulationClient, times(2)).getLoans(any());
    assertEquals(2, response.size());
  }

  @Test
  void getHoldRequestsCountForItems_shouldFetchHoldRequestsInParallel() {
    List<String> itemIds = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      itemIds.add("id_" + i);
    }
    var request = new Request();
    request.setItemId("id_1");
    request.setStatus(StatusEnum.OPEN_AWAITING_PICKUP);
    var request2 = new Request();
    request2.setItemId("id_99");
    request2.setStatus(StatusEnum.CLOSED_CANCELLED);
    when(circulationClient.getRequests(argThat(queryContains("id_1"))))
      .thenReturn(new Requests()
        .requests(List.of(request))
        .totalRecords(1));
    when(circulationClient.getRequests(argThat(queryContains("id_99"))))
      .thenReturn(new Requests()
        .requests(List.of(request2))
        .totalRecords(1));

    var response = circulationService.getHoldRequestsCountForItems(itemIds);

    verify(circulationClient, times(2)).getRequests(any());
    assertEquals(1, response.size());
  }

}
