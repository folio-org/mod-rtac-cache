package org.folio.rtaccache.perf;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

/** Samples a Hikari pool every 5ms for its peak active connections and peak threads waiting for one. */
final class PoolSampler implements AutoCloseable {

  private static final long INTERVAL_MILLIS = 5;

  private final AtomicInteger peakActive = new AtomicInteger();
  private final AtomicInteger peakWaiting = new AtomicInteger();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  private PoolSampler() {
  }

  static PoolSampler start(DataSource dataSource) throws SQLException {
    var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
    var sampler = new PoolSampler();
    sampler.scheduler.scheduleAtFixedRate(() -> {
      sampler.peakActive.accumulateAndGet(pool.getActiveConnections(), Math::max);
      sampler.peakWaiting.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max);
    }, 0, INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    return sampler;
  }

  int peakActive() {
    return peakActive.get();
  }

  int peakWaiting() {
    return peakWaiting.get();
  }

  /** Stops sampling, after any sample in progress. */
  @Override
  public void close() {
    scheduler.close();
  }
}
