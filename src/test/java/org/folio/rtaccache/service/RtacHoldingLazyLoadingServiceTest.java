package org.folio.rtaccache.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import org.folio.rtaccache.client.SearchClient;
import org.folio.rtaccache.domain.RtacBatchCountProjection;
import org.folio.rtaccache.domain.dto.ConsortiumHolding;
import org.folio.rtaccache.domain.dto.ConsortiumHoldings;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RtacHoldingLazyLoadingServiceTest {

  @Mock
  private RtacHoldingRepository rtacHoldingRepository;
  @Mock
  private RtacCacheGenerationService rtacCacheGenerationService;
  @Mock
  private SearchClient searchClient;
  @Mock
  private SystemUserScopedExecutionService executionService;
  @Mock
  private AsyncTaskExecutor taskExecutor;

  @InjectMocks
  private RtacHoldingLazyLoadingService service;

  @BeforeEach
  void setUp() {
    // Mock executor to run tasks in the same thread
    lenient().doAnswer(invocation -> {
      Runnable task = invocation.getArgument(0);
      task.run();
      return CompletableFuture.completedFuture(null);
    }).when(taskExecutor).submitCompletable(any(Runnable.class));

    // Mock execution service to run tasks in the same thread
    lenient().doAnswer(invocation -> {
      Callable<?> task = invocation.getArgument(1);
      try {
        return task.call();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }).when(executionService).executeSystemUserScoped(any(), any());
  }

  @Test
  void lazyLoadRtacHoldings_shouldNotGenerateCache_whenHoldingsExist() {
    var instanceId = UUID.randomUUID();
    when(rtacHoldingRepository.countByIdInstanceId(instanceId)).thenReturn(1);

    service.lazyLoadRtacHoldings(instanceId);

    verify(rtacCacheGenerationService, never()).generateRtacCache(any());
  }

  @Test
  void lazyLoadRtacHoldings_shouldGenerateCache_whenHoldingsDoNotExist() {
    var instanceId = UUID.randomUUID();
    when(rtacHoldingRepository.countByIdInstanceId(instanceId)).thenReturn(0);
    when(rtacCacheGenerationService.generateRtacCache(instanceId.toString()))
      .thenReturn(CompletableFuture.completedFuture(null));

    service.lazyLoadRtacHoldings(instanceId);

    verify(rtacCacheGenerationService).generateRtacCache(instanceId.toString());
    verify(rtacHoldingRepository, never()).deleteAllByIdInstanceId(any());
  }

  @Test
  void lazyLoadRtacHoldings_shouldThrowException_whenCacheGenerationFails() {
    var instanceId = UUID.randomUUID();
    when(rtacHoldingRepository.countByIdInstanceId(instanceId)).thenReturn(0);
    when(rtacCacheGenerationService.generateRtacCache(instanceId.toString()))
      .thenThrow(new RuntimeException("Generation failed"));

    assertThrows(ResponseStatusException.class, () -> service.lazyLoadRtacHoldings(instanceId));

    verify(rtacHoldingRepository).deleteAllByIdInstanceId(instanceId);
  }

  @Test
  void lazyLoadRtacHoldingsForList_shouldOnlyProcessMissingInstances() {
    var instanceId1 = UUID.randomUUID();
    var instanceId2 = UUID.randomUUID();
    var instanceId3 = UUID.randomUUID();
    var instanceIds = List.of(instanceId1, instanceId2, instanceId3);

    var projection1 = new RtacBatchCountProjection(instanceId1, 1L);
    when(rtacHoldingRepository.countBatchByIdInstanceIdIn(instanceIds)).thenReturn(List.of(projection1));

    // Mock for the inner call to lazyLoadRtacHoldings(UUID)
    when(rtacHoldingRepository.countByIdInstanceId(instanceId2)).thenReturn(0);
    when(rtacHoldingRepository.countByIdInstanceId(instanceId3)).thenReturn(0);
    when(rtacCacheGenerationService.generateRtacCache(any()))
      .thenReturn(CompletableFuture.completedFuture(null));

    service.lazyLoadRtacHoldings(instanceIds);

    verify(rtacCacheGenerationService, times(1)).generateRtacCache(instanceId2.toString());
    verify(rtacCacheGenerationService, times(1)).generateRtacCache(instanceId3.toString());
    verify(rtacCacheGenerationService, never()).generateRtacCache(instanceId1.toString());
  }

  @Test
  void lazyLoadRtacHoldingsEcs_shouldTriggerLoadingPerTenant() {
    var instanceId1 = UUID.randomUUID();
    var instanceId2 = UUID.randomUUID();
    var tenant1 = "tenant1";
    var tenant2 = "tenant2";

    var holding1 = new ConsortiumHolding().instanceId(instanceId1).tenantId(tenant1);
    var holding2 = new ConsortiumHolding().instanceId(instanceId1).tenantId(tenant2);
    var holding3 = new ConsortiumHolding().instanceId(instanceId2).tenantId(tenant2);
    var consortiumHoldings = new ConsortiumHoldings().holdings(List.of(holding1, holding2, holding3));

    when(searchClient.getConsortiumHoldings(any())).thenReturn(consortiumHoldings);
    when(rtacHoldingRepository.countByIdInstanceId(any())).thenReturn(0);
    when(rtacCacheGenerationService.generateRtacCache(any()))
      .thenReturn(CompletableFuture.completedFuture(null));

    service.lazyLoadRtacHoldingsEcs(List.of(instanceId1, instanceId2));

    verify(executionService, times(1)).executeSystemUserScoped(eq(tenant1), any());
    verify(executionService, times(2)).executeSystemUserScoped(eq(tenant2), any());
    verify(rtacCacheGenerationService, times(2)).generateRtacCache(instanceId1.toString());
    verify(rtacCacheGenerationService, times(1)).generateRtacCache(instanceId2.toString());
  }
}
