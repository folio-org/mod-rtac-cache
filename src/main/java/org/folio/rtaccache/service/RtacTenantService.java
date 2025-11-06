package org.folio.rtaccache.service;

import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.integration.RtacKafkaAdminService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
@Log4j2
public class RtacTenantService extends TenantService {

  private final RtacKafkaAdminService rtacKafkaAdminService;

  public RtacTenantService(JdbcTemplate jdbcTemplate,
    FolioExecutionContext context,
    FolioSpringLiquibase folioSpringLiquibase,
    RtacKafkaAdminService rtacKafkaAdminService) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.rtacKafkaAdminService = rtacKafkaAdminService;
  }

  @Override
  protected void afterTenantUpdate(TenantAttributes tenantAttributes) {
    rtacKafkaAdminService.restartEventListeners();
  }
}
