package org.folio.rtaccache.client;

import org.folio.rtaccache.domain.dto.HoldingsFacet;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("search")
public interface SearchClient {

  @GetMapping("/search/instances/facets")
  HoldingsFacet getHoldingsFacets(@RequestParam String query, @RequestParam String facet);
}
