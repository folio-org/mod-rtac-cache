package org.folio.rtaccache.client;

import java.util.Map;
import org.folio.rtaccache.domain.dto.Loans;
import org.folio.rtaccache.domain.dto.Requests;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface CirculationClient {

  @GetExchange("loan-storage/loans")
  Loans getLoans(@RequestParam Map<String, String> queryParametersMap);

  @GetExchange("circulation/requests")
  Requests getRequests(@RequestParam Map<String, String> queryParametersMap);
}
