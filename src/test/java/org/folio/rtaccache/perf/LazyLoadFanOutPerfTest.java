package org.folio.rtaccache.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Collections;
import java.util.UUID;
import javax.sql.DataSource;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingLazyLoadingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Lazy-loading many uncached instances fans out into many tasks that each need a DB connection.
 * Their DB work must be bounded (see {@code LazyLoadDbOperations}) so they do not queue on, and
 * time out waiting for, the Hikari pool, whatever its size.
 *
 * <p>The main check is how many threads queue on the pool: with the limit off, hundreds queue even
 * when none time out. The connection timeout is generous so a slow machine does not fail the test
 * spuriously. It still runs real lazy loads against a real pool, so its outcome can depend on the
 * hardware, which is why it is in the perf lane rather than CI. CI covers the limit itself with
 * {@code LazyLoadDbOperationsConcurrencyTest}.
 */
@TestPropertySource(properties = {
  "spring.datasource.hikari.maximum-pool-size=" + LazyLoadFanOutPerfTest.POOL_SIZE,
  "spring.datasource.hikari.connection-timeout=5000",
  "rtac.cache.db-max-concurrency=3"
})
class LazyLoadFanOutPerfTest extends BaseIntegrationTest {

  static final int POOL_SIZE = 5;
  private static final int INSTANCES = 500;
  private static final UUID INSTANCE_1 = UUID.fromString("4de861ab-af9e-4247-bc16-c547d982eb5d");

  @Autowired RtacHoldingLazyLoadingService service;
  @Autowired RtacHoldingRepository repository;
  @Autowired DataSource dataSource;

  private LoggedErrors loggedErrors;
  private PoolSampler poolSampler;

  @BeforeEach
  void startMonitoring() throws SQLException {
    loggedErrors = LoggedErrors.capture();
    poolSampler = PoolSampler.start(dataSource);
  }

  @AfterEach
  void stopMonitoring() {
    poolSampler.close();
    loggedErrors.close();
    withinTenant(TestConstant.TEST_TENANT, repository::deleteAll);
  }

  @Test
  void lazyLoadManyUncachedInstances_doesNotExhaustPool() {
    var errors = withinTenantWithResult(TestConstant.TEST_TENANT,
      () -> service.lazyLoadRtacHoldings(Collections.nCopies(INSTANCES, INSTANCE_1)));

    assertThat(errors).as("lazy-load errors").isEmpty();
    assertThat(loggedErrors.poolTimeouts()).as("logged pool timeouts").isZero();
    assertThat(poolSampler.peakWaiting()).as("peak threads queued on the pool").isLessThanOrEqualTo(POOL_SIZE);
  }
}
