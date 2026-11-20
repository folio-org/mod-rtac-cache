package org.folio.rtaccache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.folio.rtaccache.config.ResilienceConfiguration;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Checks that {@code @ConcurrencyLimit} on {@link LazyLoadDbOperations} is applied, and that all its
 * methods share one limit.
 */
@SpringJUnitConfig({ResilienceConfiguration.class, LazyLoadDbOperations.class})
@TestPropertySource(properties = "rtac.cache.db-max-concurrency=" + LazyLoadDbOperationsConcurrencyTest.LIMIT)
class LazyLoadDbOperationsConcurrencyTest {

  static final int LIMIT = 3;
  private static final int CALLERS = 12;
  private static final int METHODS = 3;
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  @Autowired LazyLoadDbOperations operations;
  @MockitoBean RtacHoldingRepository repository;
  @MockitoBean RtacHoldingBulkRepository bulkRepository;

  private final AtomicInteger inFlight = new AtomicInteger();
  private final AtomicInteger peak = new AtomicInteger();
  private final CountDownLatch limitReached = new CountDownLatch(LIMIT);
  private final CountDownLatch release = new CountDownLatch(1);

  @BeforeEach
  void blockEveryRepositoryCall() throws SQLException {
    when(repository.countByIdInstanceId(any())).thenAnswer(inv -> {
      blockUntilReleased();
      return 0;
    });
    doAnswer(inv -> {
      blockUntilReleased();
      return null;
    }).when(repository).deleteAllByIdInstanceId(any());
    doAnswer(inv -> {
      blockUntilReleased();
      return null;
    }).when(bulkRepository).bulkUpsert(any());
  }

  @Test
  void allMethodsShareOneLimit() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls = startCallers(executor);

      awaitLimitReached();
      assertLimitHolds();

      release.countDown();
      awaitAll(calls);
    }
    assertThat(peak.get()).as("peak concurrent calls").isEqualTo(LIMIT);
  }

  /** Stands in for a DB call: counts itself in flight until the test releases it. */
  private void blockUntilReleased() throws InterruptedException {
    peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
    limitReached.countDown();
    try {
      release.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } finally {
      inFlight.decrementAndGet();
    }
  }

  /** Starts more callers than the limit allows, spread across every method of {@link LazyLoadDbOperations}. */
  private List<CompletableFuture<Void>> startCallers(ExecutorService executor) {
    return IntStream.range(0, CALLERS)
      .mapToObj(i -> CompletableFuture.runAsync(() -> callMethod(i % METHODS), executor))
      .toList();
  }

  private void callMethod(int method) {
    try {
      switch (method) {
        case 0 -> operations.countByIdInstanceId(UUID.randomUUID());
        case 1 -> operations.deleteAllByIdInstanceId(UUID.randomUUID());
        default -> operations.bulkUpsert(List.of());
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private void awaitLimitReached() throws InterruptedException {
    assertThat(limitReached.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).as("limit reached").isTrue();
  }

  /** While the limit is held, no other caller may slip past it. */
  private void assertLimitHolds() {
    await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(1))
      .untilAsserted(() -> assertThat(inFlight.get()).as("calls in flight while the limit is held").isEqualTo(LIMIT));
  }

  private static void awaitAll(List<CompletableFuture<Void>> calls) throws Exception {
    CompletableFuture.allOf(calls.toArray(new CompletableFuture[0])).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
  }
}
