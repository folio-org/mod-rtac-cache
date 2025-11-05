package org.folio.rtaccache.rest;

import org.folio.rtaccache.domain.dto.RtacHoldings;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacRequest;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.service.RtacHoldingStorageService;
import org.folio.spring.data.OffsetRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/rtac-cache")
public class RtacCacheController {

  private static final Logger log = LoggerFactory.getLogger(RtacCacheController.class);

  private final RtacHoldingStorageService rtacHoldingStorageService;

  @Autowired
  public RtacCacheController(RtacHoldingStorageService rtacHoldingStorageService) {
    this.rtacHoldingStorageService = rtacHoldingStorageService;
  }

  @GetMapping("/{id}")
  public ResponseEntity<RtacHoldings> getRtacCacheHoldingsById(
    @PathVariable("id") UUID instanceId,
    @RequestParam(value = "offset", defaultValue = "0") int offset,
    @RequestParam(value = "limit", defaultValue = "100") int limit) {

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

  @PostMapping("/batch")
  public ResponseEntity<RtacHoldingsBatch> postRtacCacheBatch(
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
