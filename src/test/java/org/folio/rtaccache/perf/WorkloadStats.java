package org.folio.rtaccache.perf;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.folio.rtaccache.BaseIntegrationTest.ThrowingRunnable;

/** Counts, failures and latency percentiles for one workload; safe to record into from many threads. */
final class WorkloadStats {

  private final ConcurrentLinkedQueue<Long> micros = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

  /** Runs and times one operation, recording a failure instead of throwing it. */
  void time(ThrowingRunnable op) {
    long t0 = System.nanoTime();
    try {
      op.run();
    } catch (Exception e) {
      failures.add(e);
    }
    micros.add((System.nanoTime() - t0) / 1_000);
  }

  Collection<Throwable> failures() {
    return failures;
  }

  long timeouts() {
    return failures.stream().filter(PoolTimeouts::isPoolTimeout).count();
  }

  @Override
  public String toString() {
    var sorted = micros.stream().mapToLong(Long::longValue).sorted().toArray();
    return "ops=" + sorted.length + " failures=" + failures.size() + " timeouts=" + timeouts()
      + " p50Ms=" + percentileMillis(sorted, 50) + " p99Ms=" + percentileMillis(sorted, 99)
      + " maxMs=" + percentileMillis(sorted, 100);
  }

  private static String percentileMillis(long[] sorted, int p) {
    if (sorted.length == 0) {
      return "-";
    }
    int index = Math.min(sorted.length - 1, (int) Math.ceil(p / 100.0 * sorted.length) - 1);
    return String.format("%.1f", sorted[Math.max(index, 0)] / 1000.0);
  }
}
