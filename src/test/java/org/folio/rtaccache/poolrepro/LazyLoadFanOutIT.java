package org.folio.rtaccache.poolrepro;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import java.sql.SQLTransientConnectionException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingLazyLoadingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Lazy-loading many uncached instances fans out into many tasks that each need a DB connection.
 * Their DB work must be bounded (see {@code DbConcurrencyLimiter}) so they do not queue on, and
 * time out waiting for, the Hikari pool, whatever its size.
 */
@TestPropertySource(properties = {
  "spring.datasource.hikari.maximum-pool-size=5",
  "spring.datasource.hikari.connection-timeout=250"
})
class LazyLoadFanOutIT extends BaseIntegrationTest {

  private static final UUID INSTANCE_1 = UUID.fromString("4de861ab-af9e-4247-bc16-c547d982eb5d");

  @Autowired RtacHoldingLazyLoadingService service;
  @Autowired RtacHoldingRepository repository;
  @Autowired DataSource dataSource;

  @Test
  void manyUncachedInstances_queueOnPool() throws Exception {
    // Pool timeouts inside saveHolding are caught and only logged, so capture the log to see them.
    var captured = new CopyOnWriteArrayList<Throwable>();
    var appender = new AbstractAppender("poolReproCapture", null, null, true, Property.EMPTY_ARRAY) {
      @Override public void append(LogEvent event) {
        if (event.getThrown() != null) { captured.add(event.getThrown()); }
      }
    };
    appender.start();
    var ctx = (LoggerContext) LogManager.getContext(false);
    ctx.getConfiguration().getRootLogger().addAppender(appender, null, null);
    ctx.updateLoggers();
    var hikari = dataSource.unwrap(HikariDataSource.class);
    var peakActive = new AtomicInteger();
    var peakWaiting = new AtomicInteger();
    var sampler = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        var mx = hikari.getHikariPoolMXBean();
        peakActive.accumulateAndGet(mx.getActiveConnections(), Math::max);
        peakWaiting.accumulateAndGet(mx.getThreadsAwaitingConnection(), Math::max);
        try { Thread.sleep(5); } catch (InterruptedException e) { return; }
      }
    });
    sampler.start();

    var ids = Collections.nCopies(500, INSTANCE_1);
    var errors = withinTenantWithResult(TestConstant.TEST_TENANT, () -> service.lazyLoadRtacHoldings(ids));
    sampler.interrupt();
    ctx.getConfiguration().getRootLogger().removeAppender("poolReproCapture");
    ctx.updateLoggers();
    long timeouts = captured.stream().filter(LazyLoadFanOutIT::hasTimeoutCause).count();
    System.out.println("LAZY-LOAD-FANOUT pool=5 instances=500 peakActive=" + peakActive.get()
      + " peakWaiting=" + peakWaiting.get() + " errors=" + errors.size() + " poolTimeoutsLogged=" + timeouts);
    errors.stream().limit(3).forEach(e -> System.out.println("LAZY-LOAD-FANOUT error: " + e));
    withinTenant(TestConstant.TEST_TENANT, repository::deleteAll);
    // Both assertions fail on v1.1.1 and should pass once DB-using concurrency is bounded.
    assertThat(timeouts).as("pool timeouts (logged or thrown); expect none").isZero();
    assertThat(peakWaiting.get()).as("threads queued on the pool; expect <= pool size (5)").isLessThanOrEqualTo(5);
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
