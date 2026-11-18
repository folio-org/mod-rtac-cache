package org.folio.rtaccache.perf;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Captures every throwable logged while open. Lazy loading catches some failures, pool timeouts
 * among them, and only logs them, so the log is the only place to see them.
 */
final class LoggedErrors implements AutoCloseable {

  private static final String APPENDER_NAME = "perfLoggedErrors";

  private final List<Throwable> errors = new CopyOnWriteArrayList<>();

  private LoggedErrors() {
  }

  static LoggedErrors capture() {
    var captured = new LoggedErrors();
    var appender = new AbstractAppender(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY) {
      @Override
      public void append(LogEvent event) {
        if (event.getThrown() != null) {
          captured.errors.add(event.getThrown());
        }
      }
    };
    appender.start();
    var ctx = loggerContext();
    ctx.getConfiguration().getRootLogger().addAppender(appender, null, null);
    ctx.updateLoggers();
    return captured;
  }

  long poolTimeouts() {
    return errors.stream().filter(PoolTimeouts::isPoolTimeout).count();
  }

  @Override
  public void close() {
    var ctx = loggerContext();
    ctx.getConfiguration().getRootLogger().removeAppender(APPENDER_NAME);
    ctx.updateLoggers();
  }

  private static LoggerContext loggerContext() {
    return (LoggerContext) LogManager.getContext(false);
  }
}
