package org.folio.rtaccache.client;

import org.folio.rtaccache.domain.dto.UserTenants;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface UserClient {

  @GetExchange("user-tenants")
  UserTenants getUserTenants();
}
