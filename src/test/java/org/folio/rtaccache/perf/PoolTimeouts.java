package org.folio.rtaccache.perf;

import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Recognizes Hikari pool timeouts, however deeply Spring or lazy loading has wrapped them. */
final class PoolTimeouts {

  private PoolTimeouts() {
  }

  static boolean isPoolTimeout(Throwable throwable) {
    for (var cause = throwable; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLTransientConnectionException) {
        return true;
      }
    }
    return false;
  }

  /**
   * Where a pool timeout happened. {@code batchCheck}: the request's own check of which instances are
   * cached, before it fans out. {@code lazyLoadTask}: one of the fan-out's tasks.
   */
  static String site(Throwable throwable) {
    var frames = new ArrayList<StackTraceElement>();
    for (var cause = throwable; cause != null; cause = cause.getCause()) {
      frames.addAll(Arrays.asList(cause.getStackTrace()));
    }
    if (frames.stream().anyMatch(f -> f.getClassName().endsWith(".LazyLoadDbOperations"))) {
      return "lazyLoadTask";
    }
    if (frames.stream().anyMatch(f -> f.getMethodName().equals("countBatchByIdInstanceIdIn"))) {
      return "batchCheck";
    }
    return "other";
  }

  /** How many of {@code failures} are pool timeouts, counted by {@link #site}. */
  static Map<String, Long> bySite(Collection<Throwable> failures) {
    return failures.stream()
      .filter(PoolTimeouts::isPoolTimeout)
      .collect(Collectors.groupingBy(PoolTimeouts::site, TreeMap::new, Collectors.counting()));
  }
}
