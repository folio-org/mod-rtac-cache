package org.folio.rtaccache.client;

import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.Loans;
import org.folio.rtaccache.domain.dto.Requests;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(value = "circulation")
public interface CirculationClient {

  @GetMapping("/loan-storage/loans")
  Loans getLoans(@SpringQueryMap FolioCqlRequest request);

  @GetMapping("/circulation/requests")
  Requests getRequests(@SpringQueryMap FolioCqlRequest request);
}
