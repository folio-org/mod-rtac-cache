package org.folio.rtaccache.client;

import org.folio.rtaccache.domain.dto.UserTenants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("users")
public interface UsersClient {

  @GetMapping("/user-tenants")
  UserTenants getUserTenants();
}
