package org.folio.rtaccache.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.rtaccache.client.InventoryClient;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.BoundWithPart;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.Instance;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.ItemStatus.NameEnum;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RtacCacheGenerationService {

  @Qualifier("applicationTaskExecutor")
  private final AsyncTaskExecutor taskExecutor;
  private final InventoryClient inventoryClient;
  private final RtacHoldingBulkRepository rtacHoldingBulkRepository;
  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final CirculationService circulationService;
  private final OrdersService ordersService;
  private final ConsortiaService consortiaService;
  private final SystemUserScopedExecutionService systemUserExecutionService;
  private final FolioExecutionContext folioExecutionContext;
  private static final Integer HOLDINGS_BATCH_SIZE = 50;
  private static final Integer ITEMS_BATCH_SIZE = 500;
  private static final String CONSORTIUM_SOURCE = "CONSORTIUM";
  private static final Integer BOUND_WITH_BATCH_SIZE = 500;

  public CompletableFuture<Void> generateRtacCache(String instanceId) {
    log.info("Started RTAC cache generation for instance id: {} in tenant: {}", instanceId, folioExecutionContext.getTenantId());
    var holdingsOffset = 0;
    var instance = getInstanceById(instanceId);
    if (instance == null) {
      log.warn("Instance with id: {} not found. RTAC cache generation aborted.", instanceId);
      return CompletableFuture.completedFuture(null);
    }
    var totalHoldings = getHoldingsTotalRecords(instanceId);
    var futures = new ArrayList<CompletableFuture<Void>>();
    while (totalHoldings != 0 && holdingsOffset < totalHoldings) {
      var holdingsCql = getHoldingsByInstanceIdCql(instanceId);
      var holdingsRequest = new FolioCqlRequest(holdingsCql, HOLDINGS_BATCH_SIZE, holdingsOffset);
      var holdingsResponse = inventoryClient.getHoldings(holdingsRequest);
      for (var holding : holdingsResponse.getHoldingsRecords()) {
        futures.add(taskExecutor.submitCompletable(processIndividualHolding(instance, holding)));
      }
      holdingsOffset += HOLDINGS_BATCH_SIZE;
    }
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  private Runnable processIndividualHolding(Instance instance, HoldingsRecord holding) {
    return () -> {
      log.info("Processing holding id : {}", holding.getId());
      saveHolding(instance, holding);
      var itemsFuture = processDirectItemsForHolding(instance, holding)
        .thenCompose(v -> processItemsBoundWithHolding(instance, holding));
      var piecesFuture = processPiecesForHolding(instance, holding);
      var centralPiecesFuture = processPiecesInCentralForHolding(instance, holding);
      itemsFuture.join();
      piecesFuture.join();
      centralPiecesFuture.join();
    };
  }

  private void saveHolding(Instance instance, HoldingsRecord holding) {
    var rtacHolding = rtacHoldingMappingService.mapFrom(holding);
    var entityId = RtacHoldingId.from(rtacHolding);
    var rtacHoldingEntity = new RtacHoldingEntity(entityId, isInstanceShared(instance), rtacHolding, Instant.now());
    try {
      rtacHoldingBulkRepository.bulkUpsert(List.of(rtacHoldingEntity));
    } catch (Exception e) {
      log.error("Error during bulk upsert of RTAC holdings for holding: {}", e.getMessage(), e);
    }
  }

  private CompletableFuture<Void> processDirectItemsForHolding(Instance instance, HoldingsRecord holding) {
    var itemsOffset = 0;
    var totalItems = getItemsTotalRecords(holding.getId());
    var futures = new ArrayList<CompletableFuture<Void>>();
    while (totalItems != 0 && itemsOffset < totalItems) {
      var itemsCql = getByHoldingsIdCql(holding.getId());
      var itemsRequest = new FolioCqlRequest(itemsCql, ITEMS_BATCH_SIZE, itemsOffset);
      futures.add(processItemsBatch(instance, holding, itemsRequest));
      itemsOffset += ITEMS_BATCH_SIZE;
    }
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  private CompletableFuture<Void> processItemsBoundWithHolding(Instance instance, HoldingsRecord holdings) {
    log.info("Processing bound-with items for holding id : {}", holdings.getId());
    var boundWithPartOffset = 0;
    var totalBoundWithParts = getBoundWithTotal(holdings);
    log.info("Total bound-with parts for holding id {} : {}", holdings.getId(), totalBoundWithParts);
    var futures = new ArrayList<CompletableFuture<Void>>();
    while (totalBoundWithParts != 0 && boundWithPartOffset < totalBoundWithParts) {
      var boundWithPartsCql = getByHoldingsIdCql(holdings.getId());
      var boundWithPartsRequest = new FolioCqlRequest(boundWithPartsCql, ITEMS_BATCH_SIZE, boundWithPartOffset);
      futures.add(processBondWithItemBatch(instance, holdings, boundWithPartsRequest));
      boundWithPartOffset += BOUND_WITH_BATCH_SIZE;
    }
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  private CompletableFuture<Void> processBondWithItemBatch(Instance instance, HoldingsRecord holdings, FolioCqlRequest boundWithPartsRequest) {
    return CompletableFuture.supplyAsync(() -> {
      var boundWithPartsResponse = inventoryClient.getBoundWithParts(boundWithPartsRequest);
      log.info("Fetched {} bound-with parts for holding id: {}", boundWithPartsResponse.getTotalRecords(), holdings.getId());
      return boundWithPartsResponse.getBoundWithParts()
        .stream()
        .map(BoundWithPart::getItemId)
        .toList();
    }, taskExecutor)
      .thenComposeAsync(boundWithItemIds -> {
        if (CollectionUtils.isEmpty(boundWithItemIds)) {
          log.info("No bound-with itemIds collected for holding id: {}", holdings.getId());
          return CompletableFuture.completedFuture(null);
        }
        var queryParamValue = buildIdOrCql(boundWithItemIds);
        var folioCqlRequest = new FolioCqlRequest(queryParamValue, boundWithItemIds.size(), 0);
        return processItemsBatch(instance, holdings, folioCqlRequest);
      }, taskExecutor);
  }

  private CompletableFuture<Void> processPiecesForHolding(Instance instance, HoldingsRecord holding) {
    return CompletableFuture.supplyAsync(() -> {
      log.info("Sending request for pieces for holding id: {}", holding.getId());
      return ordersService.getPiecesByHoldingId(holding.getId());
    }, taskExecutor).thenAcceptAsync(response -> {
      log.info("Processing pieces for holding id: {}", holding.getId());
      if (response.getTotalRecords() != 0) {
        var pieces = response.getPieces();
        var rtacHoldings = pieces.stream()
          .map(piece -> rtacHoldingMappingService.mapFrom(holding, piece))
          .map(rtacHolding -> new RtacHoldingEntity(
            RtacHoldingId.from(rtacHolding), isInstanceShared(instance), rtacHolding, Instant.now()))
          .toList();
        try {
          rtacHoldingBulkRepository.bulkUpsert(rtacHoldings);
          log.info("Saved pieces for holding: {}", holding.getId());
        } catch (Exception e) {
          log.error("Error during bulk upsert of RTAC holdings for pieces: {}", e.getMessage(), e);
        }
      }
    }, taskExecutor);
  }

  private CompletableFuture<Void> processPiecesInCentralForHolding(Instance instance, HoldingsRecord holding) {
    return CompletableFuture.supplyAsync(consortiaService::getCentralTenantId, taskExecutor)
      .thenAcceptAsync(centralTenantId -> {
        if(centralTenantId.isEmpty()) {
          return;
        }
        log.info("Sending request for pieces in central tenant for holding id: {}", holding.getId());
        systemUserExecutionService.executeSystemUserScoped(centralTenantId.get(), () -> {
          processPiecesForHolding(instance, holding).join();
          return null;
        });
      }, taskExecutor);
  }

  private CompletableFuture<Void> processItemsBatch(Instance instance, HoldingsRecord holding, FolioCqlRequest request) {
    return CompletableFuture.supplyAsync(() -> {
      log.info("Sending request for items batch for holding id: {}, offset {}", holding.getId(), request.getOffset());
      var itemsResponse = inventoryClient.getItems(request);
      return itemsResponse.getItems();
    }, taskExecutor).thenAcceptAsync(items -> {
      log.info("Processing items batch {}", request.getOffset());
      var itemsHoldCountMap = retrieveItemsHoldCountMap(items);
      var itemsLoanDueDateMap = retrieveItemsLoanDueDateMap(items);
      var rtacHoldings = items.stream()
        .map(item -> processIndividualItem(instance, holding, item, itemsLoanDueDateMap, itemsHoldCountMap))
        .toList();
      try {
        rtacHoldingBulkRepository.bulkUpsert(rtacHoldings);
        log.info("Saved items batch for holding: {} offset: {}", holding.getId(), request.getOffset());
      } catch (Exception e) {
        log.error("Error during bulk upsert of RTAC holdings: {}", e.getMessage(), e);
      }
    }, taskExecutor);
  }

  private Instance getInstanceById(String instanceId) {
    var cql = "id==" + instanceId;
    var request = new FolioCqlRequest(cql, 1, 0);
    var response = inventoryClient.getInstances(request);
    if (response.getTotalRecords() == 0) {
      return null;
    }
    return response.getInstances().get(0);
  }

  private boolean isInstanceShared(Instance instance) {
    return instance.getSource() != null && instance.getSource().contains(CONSORTIUM_SOURCE);
  }

  private RtacHoldingEntity processIndividualItem(Instance instance, HoldingsRecord holding, Item item,
    Map<String, Date> dueDateMap, Map<String, Long> holdCountMap) {
    var rtacHolding = rtacHoldingMappingService.mapFrom(holding, item);
    rtacHolding.setDueDate(dueDateMap.getOrDefault(rtacHolding.getId(), null));
    rtacHolding.setTotalHoldRequests(Math.toIntExact(holdCountMap.getOrDefault(rtacHolding.getId(), 0L)));
    rtacHolding.setIsBoundWith(isItemBoundWithHoldings(item, holding));
    var entityId = RtacHoldingId.from(rtacHolding);
    return new RtacHoldingEntity(entityId, isInstanceShared(instance), rtacHolding, Instant.now());
  }

  private Map<String, Date> retrieveItemsLoanDueDateMap(List<Item> items) {
    var itemIds = items.stream().filter(this::isItemHasLoans).map(Item::getId).toList();
    return circulationService.getLoanDueDatesForItems(itemIds);
  }

  private Boolean isItemHasLoans(Item item) {
    return !NameEnum.AVAILABLE.equals(item.getStatus().getName());
  }

  private Map<String, Long> retrieveItemsHoldCountMap(List<Item> items) {
    var itemIds = items.stream().filter(this::isItemHasRequests).map(Item::getId).toList();
    return circulationService.getHoldRequestsCountForItems(itemIds);
  }

  private Boolean isItemHasRequests(Item item) {
    return !NameEnum.AVAILABLE.equals(item.getStatus().getName());
  }

  private String getHoldingsByInstanceIdCql(String instanceId) {
    return "instanceId==" + instanceId;
  }

  private Integer getHoldingsTotalRecords(String instanceId) {
    var holdingsResponse = inventoryClient.getHoldings(
      new FolioCqlRequest(getHoldingsByInstanceIdCql(instanceId), 0, 0)
    );
    return holdingsResponse.getTotalRecords();
  }

  private String getByHoldingsIdCql(String holdingsId) {
    return "holdingsRecordId==" + holdingsId;
  }

  private Integer getItemsTotalRecords(String holdingId) {
    var itemsResponse = inventoryClient.getItems(
      new FolioCqlRequest(getByHoldingsIdCql(holdingId), 0, 0)
    );
    return itemsResponse.getTotalRecords();
  }

  private int getBoundWithTotal(HoldingsRecord holdings) {
    var getByHoldingsIdCql = getByHoldingsIdCql(holdings.getId());
    var request = new FolioCqlRequest(getByHoldingsIdCql, 0, 0);
    var response = inventoryClient.getBoundWithParts(request);
    return response.getTotalRecords();
  }

  private String buildIdOrCql(List<String> ids) {
    return ids.stream().map(id -> "id==" + id).collect(java.util.stream.Collectors.joining(" or "));
  }

  private boolean isItemBoundWithHoldings(Item item, HoldingsRecord holdings) {
    return !StringUtils.equals(item.getHoldingsRecordId(), holdings.getId());
  }

}
