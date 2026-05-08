package org.folio.rtaccache.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.folio.rtaccache.client.SearchClient;
import org.folio.rtaccache.domain.RtacBatchCountProjection;
import org.folio.rtaccache.domain.dto.BatchIdsDto;
import org.folio.rtaccache.domain.dto.BatchIdsDto.IdentifierTypeEnum;
import org.folio.rtaccache.domain.dto.ConsortiumHoldings;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RtacHoldingLazyLoadingService {

  private final RtacHoldingRepository rtacHoldingRepository;
  private final RtacCacheGenerationService rtacCacheGenerationService;
  private final SearchClient searchClient;
  private final SystemUserScopedExecutionService executionService;
  @Qualifier("applicationTaskExecutor")
  private final AsyncTaskExecutor taskExecutor;

  public void lazyLoadRtacHoldings(UUID instanceId) {
    if (rtacHoldingRepository.countByIdInstanceId(instanceId) == 0) {
      try {
        var future = rtacCacheGenerationService.generateRtacCache(instanceId.toString());
        future.join();
      } catch (Exception ex) {
        Throwable rootCause = unwrapCompletionException(ex);
        if (isNotFound(rootCause)) {
          log.warn("Instance {} not found during RTAC cache generation; skipping.", instanceId);
          rtacHoldingRepository.deleteAllByIdInstanceId(instanceId);
          return;
        }
        log.error("RTAC cache generation failed for instanceId: {}", instanceId, ex);
        rtacHoldingRepository.deleteAllByIdInstanceId(instanceId);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          String.format("RTAC cache generation failed for instanceId: %s", instanceId));
      }
    }
  }

  public List<Throwable> lazyLoadRtacHoldings(List<UUID> instanceIds) {
    var foundInstanceIds = rtacHoldingRepository.countBatchByIdInstanceIdIn(instanceIds).stream()
      .map(RtacBatchCountProjection::instanceId).toList();
    var missingInstanceIds = instanceIds.stream().filter(id -> !foundInstanceIds.contains(id))
      .toList();
    var futures = new ArrayList<CompletableFuture<Void>>();
    for (var instanceId : missingInstanceIds) {
      futures.add(taskExecutor.submitCompletable(() -> lazyLoadRtacHoldings(instanceId)));
    }
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    } catch (Exception e) {
      log.error("Error during lazy loading RTAC holdings for instanceIds: {}", missingInstanceIds, e);
      return gatherExceptions(futures);
    }
    return Collections.emptyList();
  }

  public List<Throwable> lazyLoadRtacHoldingsEcs(List<UUID> instanceIds) {
    var consortiumHoldings = searchClient.getConsortiumHoldings(getBatchIdsDto(instanceIds));
    var instanceTenantMap = instanceTenantMap(consortiumHoldings);
    var futures = new ArrayList<CompletableFuture<Void>>();
    for (var entry : instanceTenantMap.entrySet()) {
      var instanceId = entry.getKey();
      var tenantIds = entry.getValue();
      for (var tenantId : tenantIds) {
        futures.add(taskExecutor.submitCompletable(() -> {
          try {
            executionService.executeSystemUserScoped(tenantId, () -> {
              lazyLoadRtacHoldings(instanceId);
              return null;
            });
          } catch (Exception e) {
            log.error("Error during lazy loading RTAC holdings for instanceId: {} in tenant: {}", instanceId, tenantId, e);
          }
        }));
      }
    }
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    } catch (Exception e) {
      log.error("Error during lazy loading RTAC holdings for instanceIds: {}", instanceTenantMap.keySet(), e);
      return gatherExceptions(futures);
    }
    return Collections.emptyList();
  }

  private BatchIdsDto getBatchIdsDto(List<UUID> instanceId) {
    var batchIdsDto = new BatchIdsDto();
    batchIdsDto.setIdentifierValues(instanceId.stream().map(UUID::toString).toList());
    batchIdsDto.setIdentifierType(IdentifierTypeEnum.INSTANCE_ID);
    return batchIdsDto;
  }

  private HashMap<UUID, Set<String>> instanceTenantMap(ConsortiumHoldings consortiumHoldings) {
    var instanceTenantMap = new HashMap<UUID, Set<String>>();
    for (var holding : consortiumHoldings.getHoldings()) {
      if (holding.getInstanceId() != null && holding.getTenantId() != null) {
        instanceTenantMap
          .computeIfAbsent(holding.getInstanceId(), k -> new HashSet<>())
          .add(holding.getTenantId());
      }
    }
    return instanceTenantMap;
  }

  private List<Throwable> gatherExceptions(List<CompletableFuture<Void>> futures) {
    return futures.stream()
      .filter(CompletableFuture::isCompletedExceptionally)
      .map(future -> {
        try {
          future.join();
          return null; // Should not happen
        } catch (Exception e) {
          // Prefer the thrown exception (it has the useful status/response info),
          // not just future.exceptionNow() which is often a generic CompletionException.
          return unwrapCompletionException(e);
        }
      })
      .filter(Objects::nonNull)
      // Missing instances should surface as "not found" errors at the API layer, not as 500s from lazy load.
      .filter(ex -> !isNotFound(ex))
      .toList();
  }

  private static Throwable unwrapCompletionException(Throwable throwable) {
    Throwable current = throwable;
    while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static boolean isNotFound(Throwable throwable) {
    if (throwable instanceof ResponseStatusException rse) {
      return rse.getStatusCode() == HttpStatus.NOT_FOUND;
    }
    if (throwable instanceof RestClientResponseException rcre) {
      return rcre.getStatusCode().value() == HttpStatus.NOT_FOUND.value();
    }
    return false;
  }
}
