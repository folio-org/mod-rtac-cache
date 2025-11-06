package org.folio.rtaccache.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.folio.rtaccache.client.InventoryClient;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.ItemStatus.NameEnum;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RtacCacheGenerationService {

  private final InventoryClient inventoryClient;
  private final RtacHoldingBulkRepository rtacHoldingRepository;
  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final CirculationService circulationService;
  private final OrdersService ordersService;
  private final AsyncTaskExecutor taskExecutor;
  private static final Integer HOLDINGS_BATCH_SIZE = 50;
  private static final Integer ITEMS_BATCH_SIZE = 500;

  public void generateRtacCache(String instanceId) {
    log.info("Started RTAC cache generation for instance id: {}", instanceId);
    var holdingsOffset = 0;
    var totalHoldings = getHoldingsTotalRecords(instanceId);
    var futures = new ArrayList<CompletableFuture>();
    while (totalHoldings != 0 && holdingsOffset < totalHoldings) {
      var holdingsCql = getHoldingsByInstanceIdCql(instanceId);
      var holdingsRequest = new FolioCqlRequest(holdingsCql, HOLDINGS_BATCH_SIZE, holdingsOffset);
      var holdingsResponse = inventoryClient.getHoldings(holdingsRequest);
      for (var holding : holdingsResponse.getHoldingsRecords()) {
        futures.add(taskExecutor.submitCompletable(processIndividualHolding(holding)));
      }
      futures.stream().forEach(CompletableFuture::join);
      holdingsOffset += HOLDINGS_BATCH_SIZE;
    }
    log.info("Finished RTAC cache generation for instance id: {}", instanceId);
  }

  private Runnable processIndividualHolding(HoldingsRecord holding) {
    return () -> {
      log.info("Processing holding id : {}", holding.getId());
      var itemsFuture = processItemsForHolding(holding);
      var piecesFuture = processPiecesForHolding(holding);
      itemsFuture.join();
      piecesFuture.join();
    };
  }

  private CompletableFuture<Void> processItemsForHolding(HoldingsRecord holding) {
    var itemsOffset = 0;
    var totalItems = getItemsTotalRecords(holding.getId());
    var futures = new ArrayList<CompletableFuture>();
    while (totalItems != 0 && itemsOffset < totalItems) {
      var itemsCql = getItemsByHoldingIdCql(holding.getId());
      var itemsRequest = new FolioCqlRequest(itemsCql, ITEMS_BATCH_SIZE, itemsOffset);
      futures.add(processItemsBatch(holding, itemsRequest));
      itemsOffset += ITEMS_BATCH_SIZE;
    }
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  private CompletableFuture<Void> processPiecesForHolding(HoldingsRecord holding) {
    return CompletableFuture.supplyAsync(() -> {
      log.info("Sending request for pieces for holding id: {}", holding.getId());
      return ordersService.getPiecesByHoldingId(holding.getId());
    }, taskExecutor).thenAcceptAsync((response)-> {
      log.info("Processing pieces for holding id: {}", holding.getId());
      if (response.getTotalRecords() != 0) {
        var pieces = response.getPieces();
        var rtacHoldings = pieces.stream()
          .map(piece -> rtacHoldingMappingService.mapFrom(holding, piece))
          .map(rtacHolding -> new RtacHoldingEntity(RtacHoldingId.from(rtacHolding), rtacHolding, Instant.now()))
          .toList();
        try {
          rtacHoldingRepository.bulkUpsert(rtacHoldings);
          log.info("Saved pieces for holding: {}", holding.getId());
        } catch (Exception e) {
          log.error("Error during bulk upsert of RTAC holdings for pieces: {}", e.getMessage(), e);
        }
      }
    }, taskExecutor);
  }

  private CompletableFuture<Void> processItemsBatch(HoldingsRecord holding, FolioCqlRequest request) {
    return CompletableFuture.supplyAsync(() -> {
      log.info("Sending request for items batch for holding id: {}, offset {}", holding.getId(), request.getOffset());
      var itemsResponse = inventoryClient.getItems(request);
      return itemsResponse.getItems();
    }, taskExecutor).thenAcceptAsync(items -> {
      log.info("Processing items batch {}", request.getOffset());
      var itemsHoldCountMap = retrieveItemsHoldCountMap(items);
      var itemsLoanDueDateMap = retrieveItemsLoanDueDateMap(items);
      var rtacHoldings = items.stream()
        .map(item -> processIndividualItem(holding, item, itemsLoanDueDateMap, itemsHoldCountMap))
        .toList();
      try {
        rtacHoldingRepository.bulkUpsert(rtacHoldings);
        log.info("Saved items batch for holding: {} offset: {}", holding.getId(), request.getOffset());
      } catch (Exception e) {
        log.error("Error during bulk upsert of RTAC holdings: {}", e.getMessage(), e);
      }
    }, taskExecutor);
  }

  private RtacHoldingEntity processIndividualItem(HoldingsRecord holding, Item item, Map<String, Date> dueDateMap, Map<String, Long> holdCountMap) {
    var rtacHolding = rtacHoldingMappingService.mapFrom(holding, item);
    rtacHolding.setDueDate(dueDateMap.getOrDefault(rtacHolding.getId(), null));
    rtacHolding.setTotalHoldRequests(Math.toIntExact(holdCountMap.getOrDefault(rtacHolding.getId(), 0L)));
    var entityId = RtacHoldingId.from(rtacHolding);
    return new RtacHoldingEntity(entityId, rtacHolding, Instant.now());
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

  private String getItemsByHoldingIdCql(String holdingId) {
    return "holdingsRecordId==" + holdingId;
  }

  private Integer getItemsTotalRecords(String holdingId) {
    var itemsResponse = inventoryClient.getItems(
      new FolioCqlRequest(getItemsByHoldingIdCql(holdingId), 0, 0)
    );
    return itemsResponse.getTotalRecords();
  }
}
