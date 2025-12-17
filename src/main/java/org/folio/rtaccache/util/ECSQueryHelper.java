package org.folio.rtaccache.util;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ECSQueryHelper {

  private final FolioExecutionContext folioExecutionContext;

  public String buildECSSubselectQuery(List<UUID> instanceIds, List<String> tenantIds) {
    StringBuilder union = new StringBuilder("(");
    String tenantUnion = tenantIds.stream()
      .map(tenantId -> buildSelectForTenant(instanceIds, tenantId))
      .collect(Collectors.joining(" UNION ALL "));
    union.append(tenantUnion);
    union.append(")");
    return union.toString();
  }

  private String buildSelectForTenant(List<UUID> instanceIds, String tenantId) {
    StringBuilder select = new StringBuilder("(SELECT * FROM ")
      .append(folioExecutionContext.getFolioModuleMetadata().getDBSchemaName(tenantId))
      .append(".rtac_holding")
      .append(" WHERE instance_id IN (")
      .append(
        instanceIds.stream()
          .map(id -> "'" + id.toString() + "'")
          .collect(Collectors.joining(", "))
      );
    select.append(") AND shared = true)");
    return select.toString();
  }
}
