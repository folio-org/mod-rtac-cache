package org.folio.rtaccache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Valid;
import org.folio.spring.liquibase.FolioLiquibaseConfiguration;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.folio.tenant.rest.resource.TenantApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

class RtacCacheApplicationTest extends BaseIntegrationTest {

  @EnableAutoConfiguration(exclude = {FolioLiquibaseConfiguration.class})
  @RestController("folioTenantController")
  @Profile("test")
  static class TestTenantController implements TenantApi {

    @Override
    public ResponseEntity<Void> deleteTenant(String operationId) {
      return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> postTenant(@Valid TenantAttributes tenantAttributes) {
      return ResponseEntity.status(HttpStatus.CREATED).build();
    }

  }

  @Autowired
  ApplicationContext context;

  @Test
  void shouldAnswerWithTrue() {
    assertTrue(true);
  }

  /**
   * With open-in-view on, a REST request that lazy-loads holds its DB connection while it waits for
   * the fan-out, and concurrent requests exhaust the pool (see {@code PoolContentionPerfTest}).
   */
  @Test
  void openInViewIsOff() {
    assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class))
      .as("open-in-view interceptors; set spring.jpa.open-in-view=false").isEmpty();
  }

}
