package org.folio.rtaccache.service;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.folio.rtaccache.domain.dto.Request.StatusEnum.OPEN_AWAITING_DELIVERY;
import static org.folio.rtaccache.domain.dto.Request.StatusEnum.OPEN_AWAITING_PICKUP;
import static org.folio.rtaccache.domain.dto.Request.StatusEnum.OPEN_IN_TRANSIT;
import static org.folio.rtaccache.domain.dto.Request.StatusEnum.OPEN_NOT_YET_FILLED;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.client.CirculationClient;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.Request;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CirculationService {

  @Qualifier("applicationTaskExecutor")
  private final AsyncTaskExecutor taskExecutor;
  private final CirculationClient circulationClient;
  private static final Integer MAX_RECORDS = 1000;
  private static final Integer MAX_IDS_FOR_CQL = 50;

  public Map<String, Date> getLoanDueDatesForItems(List<String> itemIds) {
    if (itemIds == null || itemIds.isEmpty()) {
      return Collections.emptyMap();
    }
    return Lists.partition(itemIds, MAX_IDS_FOR_CQL).stream()
      .map(this::submitLoansBatch)
      .map(CompletableFuture::join)
      .flatMap(map -> map.entrySet().stream())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private CompletableFuture<Map<String, Date>> submitLoansBatch(List<String> itemIds) {
    return CompletableFuture.supplyAsync(() -> {
      var itemDueDateMap = new HashMap<String, Date>();
      var response = circulationClient.getLoans(getLoansBatchRequest(itemIds));
      if (response.getTotalRecords() == 0) {
        return itemDueDateMap;
      }
      for (var loan : response.getLoans()) {
        itemDueDateMap.put(loan.getItemId(), loan.getDueDate());
      }
      return itemDueDateMap;
    }, taskExecutor);
  }

  private FolioCqlRequest getLoansBatchRequest(List<String> itemIds) {
    var cql = String.format("itemId==(%s) AND status.name==open", itemIds.stream()
      .map(id -> "\"" + id + "\"")
      .collect(Collectors.joining(" OR ")));
    return new FolioCqlRequest(cql, MAX_RECORDS, 0);
  }

  public Map<String, Long> getHoldRequestsCountForItems(List<String> itemIds) {
    if (itemIds == null || itemIds.isEmpty()) {
      return Collections.emptyMap();
    }
    return Lists.partition(itemIds, MAX_IDS_FOR_CQL).stream()
      .map(this::submitHoldsBatch)
      .map(CompletableFuture::join)
      .flatMap(map -> map.entrySet().stream())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private CompletableFuture<Map<String, Long>> submitHoldsBatch(List<String> itemIds) {
    return CompletableFuture.supplyAsync(() -> {
      var itemHoldCountMap = new HashMap<String, Long>();
      var response = circulationClient.getRequests(getHoldsBatchRequest(itemIds));
      if (response.getTotalRecords() == 0) {
        return itemHoldCountMap;
      }
      var holdCounts = response.getRequests().stream()
        .filter(itemRequest -> isOpenStatus(itemRequest) && isNotEmpty(itemRequest.getItemId()))
        .collect(Collectors.groupingBy(Request::getItemId, Collectors.counting()));
      itemHoldCountMap.putAll(holdCounts);
      return itemHoldCountMap;
    }, taskExecutor);
  }

  private FolioCqlRequest getHoldsBatchRequest(List<String> itemIds) {
    var cql = String.format("itemId==(%s)", itemIds.stream()
      .map(id -> "\"" + id + "\"")
      .collect(Collectors.joining(" OR ")));
    return new FolioCqlRequest(cql, MAX_RECORDS, 0);
  }

  private boolean isOpenStatus(Request itemRequest) {
    try {
      EnumSet<Request.StatusEnum> openStatuses = EnumSet.of(
        OPEN_NOT_YET_FILLED, OPEN_AWAITING_PICKUP, OPEN_IN_TRANSIT, OPEN_AWAITING_DELIVERY);
      return openStatuses.contains(itemRequest.getStatus());
    } catch (Exception e) {
      return false;
    }
  }
}
