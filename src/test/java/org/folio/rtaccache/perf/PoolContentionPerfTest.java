package org.folio.rtaccache.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLTransientConnectionException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.Instance;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingLazyLoadingService;
import org.folio.rtaccache.service.RtacHoldingStorageService;
import org.folio.rtaccache.service.handler.impl.InstanceUpdateEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Measures DB connection-pool contention by running three workloads against one small pool at the
 * same time, and logging one {@code RTAC PERF CONTENTION} line of metrics:
 * <ul>
 *   <li>a lazy-load fan-out of many uncached instances;</li>
 *   <li>readers, standing in for REST reads of cached holdings;</li>
 *   <li>Kafka-style workers, each running a {@code @Transactional} event handler in a loop.</li>
 * </ul>
 *
 * <p>Runs in the perf lane ({@code mvn -Pperf test}). Knobs (system properties, defaults in
 * brackets): {@code perf.contention.label}, {@code perf.contention.pool} [10],
 * {@code perf.contention.timeoutMillis} [250], {@code perf.contention.limit} [8],
 * {@code perf.contention.fanOut} [true], {@code perf.contention.instances} [500],
 * {@code perf.contention.readers} [50], {@code perf.contention.readPath} [service, or repository],
 * {@code perf.contention.kafka} [10], {@code perf.contention.httpDelayMillis} [0],
 * {@code perf.contention.durationMillis} [5000, used when the fan-out is off],
 * {@code perf.contention.hangSeconds} [180].
 *
 * <p>Optional assertions, off unless set: {@code perf.contention.maxFanOutErrors},
 * {@code perf.contention.maxReadTimeouts}, {@code perf.contention.maxKafkaTimeouts}. Setting any of
 * them also fails the test if the fan-out does not finish within {@code hangSeconds}.
 */
class PoolContentionPerfTest extends BaseIntegrationTest {

  private static final UUID UNCACHED_INSTANCE = UUID.fromString("4de861ab-af9e-4247-bc16-c547d982eb5d");

  private static final String LABEL = System.getProperty("perf.contention.label", "unlabeled");
  /** Pool and limit default to the module's production defaults; the timeout is far shorter than 30s. */
  private static final int POOL = Integer.getInteger("perf.contention.pool", 10);
  private static final int TIMEOUT_MILLIS = Integer.getInteger("perf.contention.timeoutMillis", 250);
  private static final int LIMIT = Integer.getInteger("perf.contention.limit", 8);
  private static final boolean FAN_OUT = Boolean.parseBoolean(System.getProperty("perf.contention.fanOut", "true"));
  private static final int INSTANCES = Integer.getInteger("perf.contention.instances", 500);
  private static final int READERS = Integer.getInteger("perf.contention.readers", 50);
  /** {@code service} reads like the REST endpoints, including their lazy-load cache check. */
  private static final boolean READ_VIA_SERVICE =
    "service".equals(System.getProperty("perf.contention.readPath", "service"));
  private static final int KAFKA_WORKERS = Integer.getInteger("perf.contention.kafka", 10);
  private static final int HTTP_DELAY_MILLIS = Integer.getInteger("perf.contention.httpDelayMillis", 0);
  private static final long DURATION_MILLIS = Long.getLong("perf.contention.durationMillis", 5000);
  private static final long HANG_SECONDS = Long.getLong("perf.contention.hangSeconds", 180);

  @DynamicPropertySource
  static void contentionProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> POOL);
    registry.add("spring.datasource.hikari.connection-timeout", () -> TIMEOUT_MILLIS);
    registry.add("rtac.cache.db-max-concurrency", () -> LIMIT);
  }

  @Autowired RtacHoldingLazyLoadingService lazyLoadingService;
  @Autowired RtacHoldingRepository repository;
  @Autowired RtacHoldingStorageService storageService;
  @Autowired InstanceUpdateEventHandler instanceHandler;
  @Autowired DataSource dataSource;
  @MockitoBean ResourceEventUtil resourceEventUtil;

  /** The handler asks the mocked util for the event's instance; each worker supplies its own. */
  private final ThreadLocal<Instance> workerInstance = new ThreadLocal<>();

  @Test
  void runWorkloads() throws Exception {
    when(resourceEventUtil.getOldFromInventoryEvent(any(), eq(Instance.class))).thenAnswer(inv -> workerInstance.get());
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(Instance.class))).thenAnswer(inv -> workerInstance.get());
    var readInstance = seedCachedInstance();
    var kafkaInstances = new ArrayList<UUID>();
    for (int i = 0; i < KAFKA_WORKERS; i++) {
      kafkaInstances.add(seedCachedInstance());
    }
    WIRE_MOCK.setGlobalFixedDelay(HTTP_DELAY_MILLIS);

    var logged = captureLoggedThrowables();
    var hikari = dataSource.unwrap(HikariDataSource.class);
    var peakActive = new AtomicInteger();
    var peakWaiting = new AtomicInteger();
    var done = new AtomicBoolean();
    var reads = new Stats();
    var kafka = new Stats();
    List<Throwable> fanOutErrors = List.of();
    boolean hung = false;
    long start = System.nanoTime();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var background = new ArrayList<CompletableFuture<Void>>();
      background.add(CompletableFuture.runAsync(() -> {
        while (!done.get()) {
          var mx = hikari.getHikariPoolMXBean();
          peakActive.accumulateAndGet(mx.getActiveConnections(), Math::max);
          peakWaiting.accumulateAndGet(mx.getThreadsAwaitingConnection(), Math::max);
          sleep(5);
        }
      }, executor));
      for (int i = 0; i < READERS; i++) {
        background.add(CompletableFuture.runAsync(() -> withinTenant(TestConstant.TEST_TENANT, () -> {
          while (!done.get()) {
            if (READ_VIA_SERVICE) {
              reads.time(() -> storageService.getRtacHoldingsByInstanceId(readInstance, PageRequest.of(0, 5)));
            } else {
              reads.time(() -> repository.findAllByIdInstanceId(readInstance, PageRequest.of(0, 5)));
            }
          }
        }), executor));
      }
      for (var instanceId : kafkaInstances) {
        var instance = new Instance().id(instanceId.toString()).source("FOLIO").instanceFormatIds(List.of("f1"));
        background.add(CompletableFuture.runAsync(() -> withinTenant(TestConstant.TEST_TENANT, () -> {
          workerInstance.set(instance);
          while (!done.get()) {
            kafka.time(() -> instanceHandler.handle(new InventoryResourceEvent()));
          }
        }), executor));
      }

      try {
        if (FAN_OUT) {
          fanOutErrors = CompletableFuture.supplyAsync(() -> withinTenantWithResult(TestConstant.TEST_TENANT,
              () -> lazyLoadingService.lazyLoadRtacHoldings(Collections.nCopies(INSTANCES, UNCACHED_INSTANCE))),
            executor).get(HANG_SECONDS, TimeUnit.SECONDS);
        } else {
          sleep(DURATION_MILLIS);
        }
      } catch (TimeoutException e) {
        hung = true;
        dumpThreads();
      } catch (ExecutionException e) {
        // The fan-out's own first DB call failed, before it fanned out.
        fanOutErrors = List.of(e.getCause());
      } finally {
        done.set(true);
      }
      CompletableFuture.allOf(background.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
    } finally {
      WIRE_MOCK.setGlobalFixedDelay(0);
      releaseLoggedThrowables();
    }

    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
    long loggedTimeouts = logged.stream().filter(PoolContentionPerfTest::hasTimeoutCause).count();
    System.out.println("RTAC PERF CONTENTION label=" + LABEL + " pool=" + POOL + " timeoutMillis=" + TIMEOUT_MILLIS
      + " limit=" + LIMIT + " readPath=" + (READ_VIA_SERVICE ? "service" : "repository") + " fanOut=" + FAN_OUT
      + " instances=" + INSTANCES + " readers=" + READERS
      + " kafka=" + KAFKA_WORKERS + " httpDelayMillis=" + HTTP_DELAY_MILLIS
      + " | hung=" + hung + " elapsedMillis=" + elapsedMillis
      + " fanOutErrors=" + fanOutErrors.size() + " loggedTimeouts=" + loggedTimeouts
      + " | reads " + reads + " | kafka " + kafka
      + " | peakActive=" + peakActive.get() + " peakWaiting=" + peakWaiting.get());
    reads.failures.stream().limit(2).forEach(e -> System.out.println("RTAC PERF CONTENTION read failure: " + e));
    kafka.failures.stream().limit(2).forEach(e -> System.out.println("RTAC PERF CONTENTION kafka failure: " + e));
    fanOutErrors.stream().limit(2).forEach(e -> System.out.println("RTAC PERF CONTENTION fan-out error: " + e));
    withinTenant(TestConstant.TEST_TENANT, repository::deleteAll);

    boolean anyAssertion = assertOptionalMax("perf.contention.maxFanOutErrors", fanOutErrors.size())
      | assertOptionalMax("perf.contention.maxReadTimeouts", reads.timeouts())
      | assertOptionalMax("perf.contention.maxKafkaTimeouts", kafka.timeouts());
    if (anyAssertion) {
      assertThat(hung).as("fan-out still running after %s seconds", HANG_SECONDS).isFalse();
    }
  }

  /** Asserts {@code actual <= max} when the property is set; returns whether it was set. */
  private static boolean assertOptionalMax(String propertyName, long actual) {
    String value = System.getProperty(propertyName);
    if (value == null || value.isBlank()) {
      return false;
    }
    assertThat(actual).as(propertyName).isLessThanOrEqualTo(Long.parseLong(value));
    return true;
  }

  private UUID seedCachedInstance() {
    var instanceId = UUID.randomUUID();
    withinTenant(TestConstant.TEST_TENANT, () -> repository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId, TypeEnum.HOLDING, UUID.randomUUID()), false,
      new RtacHolding().type(TypeEnum.HOLDING), Instant.now())));
    return instanceId;
  }

  /** The fan-out catches and only logs its upsert failures, so capture the log to count them. */
  private static List<Throwable> captureLoggedThrowables() {
    var captured = new CopyOnWriteArrayList<Throwable>();
    var appender = new AbstractAppender("contentionCapture", null, null, true, Property.EMPTY_ARRAY) {
      @Override public void append(LogEvent event) {
        if (event.getThrown() != null) {
          captured.add(event.getThrown());
        }
      }
    };
    appender.start();
    var ctx = (LoggerContext) LogManager.getContext(false);
    ctx.getConfiguration().getRootLogger().addAppender(appender, null, null);
    ctx.updateLoggers();
    return captured;
  }

  private static void releaseLoggedThrowables() {
    var ctx = (LoggerContext) LogManager.getContext(false);
    ctx.getConfiguration().getRootLogger().removeAppender("contentionCapture");
    ctx.updateLoggers();
  }

  private static boolean hasTimeoutCause(Throwable t) {
    for (var cur = t; cur != null; cur = cur.getCause()) {
      if (cur instanceof SQLTransientConnectionException) {
        return true;
      }
    }
    return false;
  }

  /** Writes every thread's stack, virtual threads included, to {@code target/perf-contention-<label>-threads.txt}. */
  private static void dumpThreads() {
    var file = Path.of("target", "perf-contention-" + LABEL + "-threads.txt").toAbsolutePath();
    try {
      Files.deleteIfExists(file);
      ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
        .dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN);
      System.out.println("RTAC PERF CONTENTION thread dump: " + file);
    } catch (Exception e) {
      System.out.println("RTAC PERF CONTENTION thread dump failed: " + e);
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Counts, failures and latency percentiles for one workload. */
  private static final class Stats {
    private final ConcurrentLinkedQueue<Long> micros = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

    void time(ThrowingRunnable op) {
      long t0 = System.nanoTime();
      try {
        op.run();
      } catch (Exception e) {
        failures.add(e);
      }
      micros.add((System.nanoTime() - t0) / 1_000);
    }

    long timeouts() {
      return failures.stream().filter(PoolContentionPerfTest::hasTimeoutCause).count();
    }

    @Override public String toString() {
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
}
