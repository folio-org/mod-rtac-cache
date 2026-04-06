package org.folio.rtaccache.client;

import org.folio.rtaccache.domain.dto.BatchIdsDto;
import org.folio.rtaccache.domain.dto.ConsortiumHoldings;
import org.folio.rtaccache.domain.dto.HoldingsFacet;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface SearchClient {

  @GetExchange("search/instances/facets")
  HoldingsFacet getHoldingsFacets(@RequestParam String query, @RequestParam String facet);

  @PostExchange("search/consortium/batch/holdings")
  ConsortiumHoldings getConsortiumHoldings(@RequestBody BatchIdsDto request);
}
