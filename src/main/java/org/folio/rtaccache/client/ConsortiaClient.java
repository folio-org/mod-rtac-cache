package org.folio.rtaccache.client;

import org.folio.rtaccache.domain.dto.ConsortiaTenants;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface ConsortiaClient {

  @GetExchange("consortia/{consortiaId}/tenants")
  ConsortiaTenants getConsortiaTenants(@PathVariable String consortiaId, @RequestParam int limit);

}
