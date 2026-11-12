package org.folio.rtaccache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.folio.rtaccache.config.ResilienceConfiguration;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.repository.RtacHoldingRepository;
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
@TestPropertySource(properties = "rtac.cache.db-max-concurrency=3")
class LazyLoadDbOperationsConcurrencyTest {

  private static final int LIMIT = 3;
  private static final int CALLERS = 12;

  @Autowired LazyLoadDbOperations operations;
  @MockitoBean RtacHoldingRepository repository;
  @MockitoBean RtacHoldingBulkRepository bulkRepository;

  @Test
  void allMethodsShareOneLimit() throws Exception {
    var inFlight = new AtomicInteger();
    var peak = new AtomicInteger();
    var limitReached = new CountDownLatch(LIMIT);
    var release = new CountDownLatch(1);
    Runnable blockingCall = () -> {
      peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
      limitReached.countDown();
      try {
        release.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      inFlight.decrementAndGet();
    };
    when(repository.countByIdInstanceId(any())).thenAnswer(inv -> { blockingCall.run(); return 0; });
    when(repository.countBatchByIdInstanceIdIn(any())).thenAnswer(inv -> { blockingCall.run(); return List.of(); });
    doAnswer(inv -> { blockingCall.run(); return null; }).when(repository).deleteAllByIdInstanceId(any());
    doAnswer(inv -> { blockingCall.run(); return null; }).when(bulkRepository).bulkUpsert(any());

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls = new ArrayList<CompletableFuture<Void>>();
      for (int i = 0; i < CALLERS; i++) {
        int which = i % 4;
        calls.add(CompletableFuture.runAsync(() -> callOne(which), executor));
      }

      assertThat(limitReached.await(10, TimeUnit.SECONDS)).as("limit reached").isTrue();
      // Give any caller that slipped past the limit time to show up.
      Thread.sleep(200);
      assertThat(inFlight.get()).as("calls in flight while the limit is held").isEqualTo(LIMIT);

      release.countDown();
      CompletableFuture.allOf(calls.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
    }
    assertThat(peak.get()).as("peak concurrent calls").isEqualTo(LIMIT);
  }

  private void callOne(int which) {
    try {
      switch (which) {
        case 0 -> operations.countByIdInstanceId(UUID.randomUUID());
        case 1 -> operations.countBatchByIdInstanceIdIn(List.of(UUID.randomUUID()));
        case 2 -> operations.deleteAllByIdInstanceId(UUID.randomUUID());
        default -> operations.bulkUpsert(List.of());
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
