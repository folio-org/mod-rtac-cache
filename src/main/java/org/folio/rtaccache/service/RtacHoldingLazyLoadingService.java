package org.folio.rtaccache.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
        log.error("RTAC cache generation failed for instanceId: {}", instanceId, ex);
        rtacHoldingRepository.deleteAllByIdInstanceId(instanceId);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          String.format("RTAC cache generation failed for instanceId: %s", instanceId));
      }
    }
  }

  public void lazyLoadRtacHoldings(List<UUID> instanceIds) {
    var foundInstanceIds = rtacHoldingRepository.countBatchByIdInstanceIdIn(instanceIds).stream()
      .map(RtacBatchCountProjection::instanceId).toList();
    var missingInstanceIds = instanceIds.stream().filter(id -> !foundInstanceIds.contains(id))
      .toList();
    var futures = new ArrayList<CompletableFuture<Void>>();
    for (var instanceId : missingInstanceIds) {
      futures.add(taskExecutor.submitCompletable(() -> lazyLoadRtacHoldings(instanceId)));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  }

  public void lazyLoadRtacHoldingsEcs(List<UUID> instanceIds) {
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
            throw e;
          }
        }));
      }
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
}
