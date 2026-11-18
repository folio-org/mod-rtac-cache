package org.folio.rtaccache.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
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
 * <p>The main check is how many threads queue on the pool, which does not depend on timing: with
 * the limit off, hundreds queue even when none time out. The connection timeout is generous so a
 * slow machine (such as a slow connection to the test database) does not fail the test spuriously.
 */
@TestPropertySource(properties = {
  "spring.datasource.hikari.maximum-pool-size=" + LazyLoadFanOutIT.POOL_SIZE,
  "spring.datasource.hikari.connection-timeout=5000",
  "rtac.cache.db-max-concurrency=3"
})
class LazyLoadFanOutIT extends BaseIntegrationTest {

  static final int POOL_SIZE = 5;
  private static final int INSTANCES = 500;
  private static final UUID INSTANCE_1 = UUID.fromString("4de861ab-af9e-4247-bc16-c547d982eb5d");
  private static final String APPENDER_NAME = "lazyLoadFanOutCapture";

  @Autowired RtacHoldingLazyLoadingService service;
  @Autowired RtacHoldingRepository repository;
  @Autowired DataSource dataSource;

  /** Lazy loading catches and only logs some pool timeouts, so the log is captured to see them. */
  private final List<Throwable> loggedErrors = new CopyOnWriteArrayList<>();
  private final AtomicInteger peakWaiting = new AtomicInteger();
  private ScheduledExecutorService poolSampler;

  @BeforeEach
  void startMonitoring() throws SQLException {
    captureLoggedErrors();
    samplePeakWaiting();
  }

  @AfterEach
  void stopMonitoring() {
    poolSampler.close();
    releaseLoggedErrors();
    withinTenant(TestConstant.TEST_TENANT, repository::deleteAll);
  }

  @Test
  void lazyLoadManyUncachedInstances_doesNotExhaustPool() {
    var errors = withinTenantWithResult(TestConstant.TEST_TENANT,
      () -> service.lazyLoadRtacHoldings(Collections.nCopies(INSTANCES, INSTANCE_1)));

    assertThat(errors).as("lazy-load errors").isEmpty();
    assertThat(loggedErrors).as("logged pool timeouts").noneMatch(LazyLoadFanOutIT::hasTimeoutCause);
    assertThat(peakWaiting.get()).as("peak threads queued on the pool").isLessThanOrEqualTo(POOL_SIZE);
  }

  private void captureLoggedErrors() {
    var appender = new AbstractAppender(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY) {
      @Override public void append(LogEvent event) {
        if (event.getThrown() != null) {
          loggedErrors.add(event.getThrown());
        }
      }
    };
    appender.start();
    var ctx = (LoggerContext) LogManager.getContext(false);
    ctx.getConfiguration().getRootLogger().addAppender(appender, null, null);
    ctx.updateLoggers();
  }

  private static void releaseLoggedErrors() {
    var ctx = (LoggerContext) LogManager.getContext(false);
    ctx.getConfiguration().getRootLogger().removeAppender(APPENDER_NAME);
    ctx.updateLoggers();
  }

  private void samplePeakWaiting() throws SQLException {
    var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
    poolSampler = Executors.newSingleThreadScheduledExecutor();
    poolSampler.scheduleAtFixedRate(
      () -> peakWaiting.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max),
      0, 5, TimeUnit.MILLISECONDS);
  }

  private static boolean hasTimeoutCause(Throwable t) {
    for (var cur = t; cur != null; cur = cur.getCause()) {
      if (cur instanceof SQLTransientConnectionException) {
        return true;
      }
    }
    return false;
  }
}
