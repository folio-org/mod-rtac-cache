package org.folio.rtaccache.rest;

import org.folio.rtac.rest.resource.RtacApi;
import org.folio.rtaccache.domain.dto.RtacHoldings;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacRequest;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.service.RtacHoldingStorageService;
import org.folio.spring.data.OffsetRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class RtacCacheController implements RtacApi {

  private static final Logger log = LoggerFactory.getLogger(RtacCacheController.class);

  private final RtacHoldingStorageService rtacHoldingStorageService;


  @Override
  public ResponseEntity<RtacHoldings> searchRtacCacheHoldings(UUID instanceId, String query, Boolean available, Integer offset, Integer limit) {
    log.info("Received request to search RTAC holdings by query: {}, available: {}, offset: {}, limit: {}", query, available, offset, limit);

    Page<RtacHolding> pagedRtacHoldings =
      rtacHoldingStorageService.searchRtacHoldings(instanceId, query, available, OffsetRequest.of(offset, limit));

    var rtacHoldings = new RtacHoldings();
    rtacHoldings.setHoldings(new ArrayList<>(pagedRtacHoldings.getContent()));
    rtacHoldings.setTotalRecords((int) pagedRtacHoldings.getTotalElements());

    log.info("Returning {} RTAC holdings for query: {}", rtacHoldings.getHoldings().size(), query);
    return new ResponseEntity<>(rtacHoldings, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<RtacHoldings> getRtacCacheHoldingsById(UUID instanceId, Integer offset, Integer limit) {

    log.info("Received request to get RTAC holdings by instanceId: {}, offset: {}, limit: {}", instanceId, offset, limit);

    Page<RtacHolding> pagedRtacHoldings =
      rtacHoldingStorageService.getRtacHoldingsByInstanceId(instanceId.toString(), OffsetRequest.of(offset, limit));

    var rtacHoldings = new RtacHoldings();
    rtacHoldings.setInstanceId(instanceId.toString());
    rtacHoldings.setHoldings(new ArrayList<>(pagedRtacHoldings.getContent()));
    rtacHoldings.setTotalRecords((int) pagedRtacHoldings.getTotalElements());

    log.info("Returning {} RTAC holdings for instanceId: {}", rtacHoldings.getHoldings().size(), instanceId);
    return new ResponseEntity<>(rtacHoldings, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<RtacHoldingsBatch> postRtacCacheBatchHoldings(
    @RequestBody RtacRequest rtacRequest) {

    log.info("Received batch request for RTAC holdings summary with {} instance ids", rtacRequest.getInstanceIds().size());

    List<UUID> instanceIds = rtacRequest.getInstanceIds().stream()
      .map(UUID::fromString)
      .toList();

    RtacHoldingsBatch rtacHoldingsBatch = rtacHoldingStorageService.getRtacHoldingsSummaryForInstanceIds(instanceIds);

    log.info("Returning batch summary with {} holdings and {} errors", rtacHoldingsBatch.getHoldings().size(), rtacHoldingsBatch.getErrors().size());
    return new ResponseEntity<>(rtacHoldingsBatch, HttpStatus.OK);
  }
}
