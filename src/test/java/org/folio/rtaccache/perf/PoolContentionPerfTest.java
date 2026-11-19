package org.folio.rtaccache.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.Instance;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacRequest;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingStorageService;
import org.folio.rtaccache.service.handler.impl.InstanceUpdateEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

/**
 * Measures DB connection-pool contention by running three workloads against one small pool at the
 * same time, and logging one {@code RTAC PERF CONTENTION} line of metrics:
 * <ul>
 *   <li>a lazy-load fan-out: concurrent batch requests for many uncached instances;</li>
 *   <li>readers, each reading one cached instance over and over;</li>
 *   <li>Kafka-style workers, each running a {@code @Transactional} event handler in a loop.</li>
 * </ul>
 *
 * <p>By default the batch requests and the reads go through Spring MVC (MockMvc), as real REST
 * requests do, so whatever MVC adds is in play: open-in-view, for one, makes a request hold its
 * connection while it waits for its fan-out, and with it on this test's batch requests fail.
 * {@code path=service} calls the same service methods directly instead, to compare.
 *
 * <p>Runs in the perf lane ({@code mvn -Pperf test}). Knobs (system properties, defaults in
 * brackets): {@code perf.contention.label}, {@code perf.contention.pool} [10],
 * {@code perf.contention.timeoutMillis} [2000], {@code perf.contention.limit} [8],
 * {@code perf.contention.path} [rest, or service], {@code perf.contention.fanOut} [true],
 * {@code perf.contention.batchRequests} [20], {@code perf.contention.idsPerBatch} [25],
 * {@code perf.contention.readers} [10], {@code perf.contention.kafka} [5],
 * {@code perf.contention.httpDelayMillis} [0],
 * {@code perf.contention.durationMillis} [5000, used when the fan-out is off],
 * {@code perf.contention.hangSeconds} [180].
 *
 * <p>Optional assertions, off unless set: {@code perf.contention.maxFanOutErrors} (failed batch
 * requests plus lazy-load errors in their responses), {@code perf.contention.maxReadTimeouts},
 * {@code perf.contention.maxKafkaTimeouts}. Setting any of them also fails the test if the fan-out
 * does not finish within {@code hangSeconds}.
 */
class PoolContentionPerfTest extends BaseIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(PoolContentionPerfTest.class);
  private static final UUID UNCACHED_INSTANCE = UUID.fromString("4de861ab-af9e-4247-bc16-c547d982eb5d");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String LABEL = System.getProperty("perf.contention.label", "unlabeled");
  /** Pool and limit default to the module's production defaults; the timeout is far shorter than 30s. */
  private static final int POOL = Integer.getInteger("perf.contention.pool", 10);
  private static final int TIMEOUT_MILLIS = Integer.getInteger("perf.contention.timeoutMillis", 2000);
  private static final int LIMIT = Integer.getInteger("perf.contention.limit", 8);
  private static final boolean VIA_REST = !"service".equals(System.getProperty("perf.contention.path", "rest"));
  private static final boolean FAN_OUT = Boolean.parseBoolean(System.getProperty("perf.contention.fanOut", "true"));
  private static final int BATCH_REQUESTS = Integer.getInteger("perf.contention.batchRequests", 20);
  private static final int IDS_PER_BATCH = Integer.getInteger("perf.contention.idsPerBatch", 25);
  private static final int READERS = Integer.getInteger("perf.contention.readers", 10);
  private static final int KAFKA_WORKERS = Integer.getInteger("perf.contention.kafka", 5);
  private static final int HTTP_DELAY_MILLIS = Integer.getInteger("perf.contention.httpDelayMillis", 0);
  private static final long DURATION_MILLIS = Long.getLong("perf.contention.durationMillis", 5000);
  private static final long HANG_SECONDS = Long.getLong("perf.contention.hangSeconds", 180);

  @DynamicPropertySource
  static void contentionProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> POOL);
    registry.add("spring.datasource.hikari.connection-timeout", () -> TIMEOUT_MILLIS);
    registry.add("rtac.cache.db-max-concurrency", () -> LIMIT);
  }

  @Autowired RtacHoldingRepository repository;
  @Autowired RtacHoldingStorageService storageService;
  @Autowired InstanceUpdateEventHandler instanceHandler;
  @Autowired DataSource dataSource;
  @MockitoBean ResourceEventUtil resourceEventUtil;

  /** The handler asks the mocked util for the event's instance; each Kafka worker supplies its own. */
  private final ThreadLocal<Instance> workerInstance = new ThreadLocal<>();
  /** Tells the readers and Kafka workers to stop. */
  private final AtomicBoolean done = new AtomicBoolean();
  private final WorkloadStats batches = new WorkloadStats();
  /** Lazy-load errors that batch responses reported; the requests themselves succeeded. */
  private final Collection<String> lazyLoadErrors = new ConcurrentLinkedQueue<>();
  private final WorkloadStats reads = new WorkloadStats();
  private final WorkloadStats kafka = new WorkloadStats();

  @BeforeEach
  void setUp() {
    when(resourceEventUtil.getOldFromInventoryEvent(any(), eq(Instance.class))).thenAnswer(inv -> workerInstance.get());
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(Instance.class))).thenAnswer(inv -> workerInstance.get());
    WIRE_MOCK.setGlobalFixedDelay(HTTP_DELAY_MILLIS);
  }

  @AfterEach
  void tearDown() {
    WIRE_MOCK.setGlobalFixedDelay(0);
    withinTenant(TestConstant.TEST_TENANT, repository::deleteAll);
  }

  @Test
  void runWorkloads() throws Exception {
    var readInstance = seedCachedInstance();
    var kafkaInstances = IntStream.range(0, KAFKA_WORKERS).mapToObj(i -> seedCachedInstance()).toList();

    long start = System.nanoTime();
    boolean hung;
    try (var logged = LoggedErrors.capture();
         var pool = PoolSampler.start(dataSource)) {
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var background = new ArrayList<CompletableFuture<Void>>();
        background.addAll(startReaders(executor, readInstance));
        background.addAll(startKafkaWorkers(executor, kafkaInstances));
        try {
          hung = FAN_OUT ? runFanOut() : idleForDuration();
        } finally {
          done.set(true);
        }
        CompletableFuture.allOf(background.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
      }
      report(hung, (System.nanoTime() - start) / 1_000_000, logged, pool);
    }

    assertOptionalLimits(hung);
  }

  private List<CompletableFuture<Void>> startReaders(ExecutorService executor, UUID instanceId) {
    ThrowingRunnable read = VIA_REST
      ? () -> expectOk(mockMvc.perform(get("/rtac-cache/{id}", instanceId).param("offset", "0").param("limit", "5")
          .headers(defaultHeaders(TestConstant.TEST_TENANT, APPLICATION_JSON))).andReturn().getResponse().getStatus())
      : () -> storageService.getRtacHoldingsByInstanceId(instanceId, PageRequest.of(0, 5));
    return IntStream.range(0, READERS)
      .mapToObj(i -> repeatUntilDone(executor, () -> reads.time(read)))
      .toList();
  }

  private List<CompletableFuture<Void>> startKafkaWorkers(ExecutorService executor, List<UUID> instanceIds) {
    return instanceIds.stream()
      .map(id -> new Instance().id(id.toString()).source("FOLIO").instanceFormatIds(List.of("f1")))
      .map(instance -> repeatUntilDone(executor, () -> {
        workerInstance.set(instance);
        kafka.time(() -> instanceHandler.handle(new InventoryResourceEvent()));
      }))
      .toList();
  }

  /** Runs {@code op} over and over, in the test tenant on its own virtual thread, until {@link #done} is set. */
  private CompletableFuture<Void> repeatUntilDone(ExecutorService executor, Runnable op) {
    return CompletableFuture.runAsync(() -> withinTenant(TestConstant.TEST_TENANT, () -> {
      while (!done.get()) {
        op.run();
      }
    }), executor);
  }

  /**
   * Sends the batch requests all at once, and returns whether they hung. They run on their own
   * executor so that hung requests can be abandoned instead of waited for: each one blocks in
   * {@code CompletableFuture.join}, which ignores interrupts.
   */
  private boolean runFanOut() throws InterruptedException, ExecutionException {
    var ids = Collections.nCopies(IDS_PER_BATCH, UNCACHED_INSTANCE);
    var fanOutExecutor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      var requests = IntStream.range(0, BATCH_REQUESTS)
        .mapToObj(i -> CompletableFuture.runAsync(() -> withinTenant(TestConstant.TEST_TENANT,
          () -> batches.time(() -> collectLazyLoadErrors(VIA_REST ? postBatch(ids) : summarize(ids)))), fanOutExecutor))
        .toArray(CompletableFuture[]::new);
      CompletableFuture.allOf(requests).get(HANG_SECONDS, TimeUnit.SECONDS);
      return false;
    } catch (TimeoutException e) {
      dumpThreads();
      return true;
    } finally {
      // Not close(): after a timeout that would wait for the hung requests.
      fanOutExecutor.shutdownNow();
    }
  }

  private RtacHoldingsBatch postBatch(List<UUID> ids) throws Exception {
    var response = mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TestConstant.TEST_TENANT, APPLICATION_JSON))
        .content(JSON.writeValueAsString(new RtacRequest().instanceIds(ids.stream().map(UUID::toString).toList()))))
      .andReturn().getResponse();
    expectOk(response.getStatus());
    return JSON.readValue(response.getContentAsString(), RtacHoldingsBatch.class);
  }

  /** What the batch endpoint does, without Spring MVC. */
  private RtacHoldingsBatch summarize(List<UUID> ids) {
    return storageService.getRtacHoldingsSummaryForInstanceIds(ids);
  }

  private void collectLazyLoadErrors(RtacHoldingsBatch batch) {
    batch.getErrors().stream()
      .filter(error -> String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()).equals(error.getCode()))
      .forEach(error -> lazyLoadErrors.add(error.getMessage()));
  }

  private static void expectOk(int status) {
    if (status != HttpStatus.OK.value()) {
      throw new IllegalStateException("HTTP " + status);
    }
  }

  /** Lets the readers and Kafka workers run for {@code durationMillis} when there is no fan-out. */
  private static boolean idleForDuration() throws InterruptedException, ExecutionException {
    CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(DURATION_MILLIS, TimeUnit.MILLISECONDS))
      .get();
    return false;
  }

  private void report(boolean hung, long elapsedMillis, LoggedErrors logged, PoolSampler pool) {
    log.info("RTAC PERF CONTENTION label={} pool={} timeoutMillis={} limit={} path={} fanOut={} batchRequests={}"
        + " idsPerBatch={} readers={} kafka={} httpDelayMillis={} | hung={} elapsedMillis={} lazyLoadErrors={}"
        + " loggedTimeouts={} | batches {} | reads {} | kafka {} | peakActive={} peakWaiting={}",
      LABEL, POOL, TIMEOUT_MILLIS, LIMIT, VIA_REST ? "rest" : "service", FAN_OUT, BATCH_REQUESTS, IDS_PER_BATCH,
      READERS, KAFKA_WORKERS, HTTP_DELAY_MILLIS, hung, elapsedMillis, lazyLoadErrors.size(),
      logged.poolTimeoutsBySite(), batches, reads, kafka, pool.peakActive(), pool.peakWaiting());
    logSamples("batch failure", batches.failures().stream().map(Throwable::toString).toList());
    logSamples("lazy-load error", lazyLoadErrors);
    logSamples("read failure", reads.failures().stream().map(Throwable::toString).toList());
    logSamples("kafka failure", kafka.failures().stream().map(Throwable::toString).toList());
  }

  /** Logs the first two, as text so the log capture and the console stay readable. */
  private static void logSamples(String kind, Collection<String> samples) {
    samples.stream().limit(2).forEach(sample -> log.info("RTAC PERF CONTENTION {}: {}", kind, sample));
  }

  private void assertOptionalLimits(boolean hung) {
    boolean fanOutChecked = assertOptionalMax("perf.contention.maxFanOutErrors",
      batches.failures().size() + lazyLoadErrors.size());
    boolean readsChecked = assertOptionalMax("perf.contention.maxReadTimeouts", reads.timeouts());
    boolean kafkaChecked = assertOptionalMax("perf.contention.maxKafkaTimeouts", kafka.timeouts());
    if (fanOutChecked || readsChecked || kafkaChecked) {
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

  /** Writes every thread's stack, virtual threads included, to {@code target/perf-contention-<label>-threads.txt}. */
  private static void dumpThreads() {
    var file = Path.of("target", "perf-contention-" + LABEL + "-threads.txt").toAbsolutePath();
    try {
      Files.deleteIfExists(file);
      ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
        .dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN);
      log.info("RTAC PERF CONTENTION thread dump: {}", file);
    } catch (Exception e) {
      log.info("RTAC PERF CONTENTION thread dump failed: {}", e.toString());
    }
  }
}
