package org.folio.rtaccache.perf;

import java.sql.SQLTransientConnectionException;

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
}
