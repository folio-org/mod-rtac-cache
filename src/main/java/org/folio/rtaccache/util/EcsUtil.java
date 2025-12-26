package org.folio.rtaccache.util;

import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.service.ConsortiaService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EcsUtil {

  private final FolioExecutionContext folioExecutionContext;
  private final ConsortiaService consortiaService;

  public String getSchemaName() {
    if (consortiaService.isCentralTenant()) {
      return getAllTenantsSchemaName();
    } else {
      return getCurrentTenantSchemaName();
    }
  }

  public String getCurrentTenantSchemaName() {
    return folioExecutionContext.getFolioModuleMetadata().getDBSchemaName(folioExecutionContext.getTenantId());
  }

  public String getAllTenantsSchemaName() {
    return consortiaService.getConsortiaTenants().stream()
      .map(tenantId -> folioExecutionContext.getFolioModuleMetadata().getDBSchemaName(tenantId))
      .collect(Collectors.joining(","));
  }

}
