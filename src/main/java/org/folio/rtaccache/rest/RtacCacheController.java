package org.folio.rtaccache.rest;

import org.folio.rtaccache.domain.dto.RtacHoldings;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacHoldingsSummary;
import org.folio.rtaccache.domain.dto.RtacRequest;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.service.RtacHoldingStorageService;
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

@RestController
@RequestMapping("/rtac-cache")
public class RtacCacheController {

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

    int page = offset / limit;

    Page<RtacHolding> pagedRtacHoldings =
      rtacHoldingStorageService.getRtacHoldingsByInstanceId(instanceId.toString(), page, limit);

    var rtacHoldings = new RtacHoldings();
    rtacHoldings.setInstanceId(instanceId.toString());
    rtacHoldings.setHoldings(new ArrayList<>(pagedRtacHoldings.getContent()));
    rtacHoldings.setTotalRecords((int) pagedRtacHoldings.getTotalElements());
    return new ResponseEntity<>(rtacHoldings, HttpStatus.OK);
  }

  @PostMapping("/batch")
  public ResponseEntity<RtacHoldingsBatch> postRtacCacheBatch(
    @RequestBody RtacRequest rtacRequest) {

    List<UUID> instanceIds = rtacRequest.getInstanceIds().stream()
      .map(UUID::fromString)
      .toList();

    RtacHoldingsBatch rtacHoldingsBatch = rtacHoldingStorageService.getRtacHoldingsSummaryForInstanceIds(instanceIds);
    return new ResponseEntity<>(rtacHoldingsBatch, HttpStatus.OK);
  }
}
