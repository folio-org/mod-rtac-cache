package org.folio.rtaccache.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.client.ConsortiaClient;
import org.folio.rtaccache.client.UsersClient;
import org.folio.rtaccache.domain.dto.ConsortiaTenantsTenantsInner;
import org.folio.spring.FolioExecutionContext;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConsortiaService {

  private final ConsortiaClient consortiaClient;
  private final UsersClient usersClient;
  private final FolioExecutionContext folioExecutionContext;
  public static final String IS_CENTRAL_TENANT_CACHE = "isCentralTenantIdCache";
  public static final String CENTRAL_TENANT_CACHE = "centralTenantIdCache";
  public static final String CONSORTIA_TENANTS_CACHE = "consortiaTenantsCache";
  public static final int CONSORTIA_TENANTS_LIMIT = 1000;

  @Cacheable(value = IS_CENTRAL_TENANT_CACHE, key = "@folioExecutionContext.getTenantId()")
  public boolean isCentralTenant() {
    var userTenants = usersClient.getUserTenants();
    if (userTenants.getTotalRecords() == 0) {
      return false;
    }
    return folioExecutionContext.getTenantId().equals(userTenants.getUserTenants().get(0).getCentralTenantId());
  }

  @Cacheable(value = CONSORTIA_TENANTS_CACHE, key = "@folioExecutionContext.getTenantId()")
  public List<String> getConsortiaTenants() {
    var userTenants = usersClient.getUserTenants();
    if (userTenants.getTotalRecords() == 0) {
      return Collections.emptyList();
    }
    return consortiaClient.getConsortiaTenants(userTenants.getUserTenants().get(0).getConsortiumId(),
      CONSORTIA_TENANTS_LIMIT).getTenants()
      .stream()
      .map(ConsortiaTenantsTenantsInner::getId)
      .toList();
  }

  @Cacheable(value = CENTRAL_TENANT_CACHE, key = "@folioExecutionContext.getTenantId()")
  public Optional<String> getCentralTenantId() {
    var userTenants = usersClient.getUserTenants();
    if (userTenants.getTotalRecords() == 0) {
      return Optional.empty();
    }
    return Optional.ofNullable(userTenants.getUserTenants().get(0).getCentralTenantId());
  }

}
