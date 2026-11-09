package org.folio.rtaccache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DbConcurrencyLimiterTest {

  @Test
  void neverExceedsConfiguredPermits() throws Exception {
    var limiter = new DbConcurrencyLimiter(3);
    var current = new AtomicInteger();
    var peak = new AtomicInteger();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new ArrayList<Future<?>>();
      for (int i = 0; i < 100; i++) {
        futures.add(executor.submit(() -> limiter.run(() -> {
          peak.accumulateAndGet(current.incrementAndGet(), Math::max);
          try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
          current.decrementAndGet();
        })));
      }
      for (var f : futures) { f.get(); }
    }
    assertThat(peak.get()).isLessThanOrEqualTo(3).isGreaterThan(0);
  }

  @Test
  void releasesPermitWhenActionThrows() {
    var limiter = new DbConcurrencyLimiter(1);
    assertThatThrownBy(() -> limiter.run(() -> { throw new IllegalStateException("boom"); }))
      .isInstanceOf(IllegalStateException.class);
    assertThat(limiter.<String>call(() -> "ok")).isEqualTo("ok");
  }

  @Test
  void interruptedWaiterGetsExceptionAndKeepsInterruptFlag() throws Exception {
    var limiter = new DbConcurrencyLimiter(1);
    var holding = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var holder = new Thread(() -> limiter.run(() -> {
      holding.countDown();
      try { release.await(); } catch (InterruptedException ignored) { }
    }));
    holder.start();
    holding.await();
    var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
    var waiter = new Thread(() -> {
      try { limiter.run(() -> { }); } catch (Throwable t) { failure.set(t); }
      interrupted.set(Thread.currentThread().isInterrupted());
    });
    waiter.start();
    waiter.interrupt();
    waiter.join();
    release.countDown();
    holder.join();
    assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
    assertThat(interrupted.get()).isTrue();
  }

  @Test
  void rejectsZeroPermits() {
    assertThatThrownBy(() -> new DbConcurrencyLimiter(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
