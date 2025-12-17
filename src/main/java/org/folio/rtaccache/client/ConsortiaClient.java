package org.folio.rtaccache.client;

import org.folio.rtaccache.domain.dto.ConsortiaTenants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "consortia")
public interface ConsortiaClient {

  @GetMapping("/consortia/{consortiaId}/tenants")
  ConsortiaTenants getConsortiaTenants(@PathVariable String consortiaId, @RequestParam int limit);

}
