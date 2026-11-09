package org.folio.rtaccache.service;

import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bounds how many cache-generation / lazy-load tasks may use the database at once.
 *
 * <p>Lazy loading fans out into an unbounded number of tasks that each need a pooled connection, so
 * they queue on (and time out waiting for) the Hikari pool. A permit is held only around a single
 * leaf database call, never while waiting on other tasks, so parent tasks that join child tasks
 * cannot deadlock on it.
 */
@Component
@Slf4j
public class DbConcurrencyLimiter {

  static final int POOL_HEADROOM = 2;
  static final int FALLBACK_PERMITS = 8;

  private final Semaphore semaphore;
  private final int permits;

  @Autowired
  public DbConcurrencyLimiter(ObjectProvider<DataSource> dataSource,
    @Value("${rtac.cache.db-max-concurrency:0}") int configuredPermits) {
    this(configuredPermits > 0 ? configuredPermits : derivePermits(dataSource.getIfAvailable()));
  }

  DbConcurrencyLimiter(int permits) {
    if (permits < 1) {
      throw new IllegalArgumentException("permits must be >= 1");
    }
    this.permits = permits;
    this.semaphore = new Semaphore(permits, true);
    log.info("Lazy-load/cache-generation DB concurrency limited to {} concurrent calls", permits);
  }

  public <T> T call(Supplier<T> action) {
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for a database permit", e);
    }
    try {
      return action.get();
    } finally {
      semaphore.release();
    }
  }

  public void run(Runnable action) {
    call(() -> {
      action.run();
      return null;
    });
  }

  int permits() {
    return permits;
  }

  private static int derivePermits(DataSource dataSource) {
    try {
      if (dataSource != null && dataSource.isWrapperFor(HikariDataSource.class)) {
        var max = dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize();
        return Math.max(1, max - POOL_HEADROOM);
      }
    } catch (Exception e) {
      log.warn("Could not read Hikari maximum pool size; using {} permits", FALLBACK_PERMITS, e);
    }
    return FALLBACK_PERMITS;
  }
}
