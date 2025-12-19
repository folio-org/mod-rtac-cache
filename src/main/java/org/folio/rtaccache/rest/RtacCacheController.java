package org.folio.rtaccache.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtac.rest.resource.RtacApi;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHoldings;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacPreWarmingJob;
import org.folio.rtaccache.domain.dto.RtacPreWarmingJobs;
import org.folio.rtaccache.domain.dto.RtacRequest;
import org.folio.rtaccache.domain.dto.RtacSubmitPreWarming;
import org.folio.rtaccache.service.RtacCachePreWarmingService;
import org.folio.rtaccache.service.RtacHoldingStorageService;
import org.folio.spring.data.OffsetRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class RtacCacheController implements RtacApi {

  private static final Logger log = LoggerFactory.getLogger(RtacCacheController.class);

  private final RtacHoldingStorageService rtacHoldingStorageService;
  private final RtacCachePreWarmingService rtacCachePreWarmingService;


  @Override
  public ResponseEntity<RtacHoldings> searchRtacCacheHoldings(UUID instanceId, String query, Boolean available, List<String> sort, Integer offset, Integer limit) {
    log.info("Received request to search RTAC holdings by query: {}, available: {}, offset: {}, limit: {}", query, available, offset, limit);

    Page<RtacHolding> pagedRtacHoldings =
      rtacHoldingStorageService.searchRtacHoldings(instanceId, query, available, buildPageable(offset, limit, sort));

    var rtacHoldings = new RtacHoldings();
    rtacHoldings.setHoldings(new ArrayList<>(pagedRtacHoldings.getContent()));
    rtacHoldings.setTotalRecords((int) pagedRtacHoldings.getTotalElements());

    log.info("Returning {} RTAC holdings for query: {}", rtacHoldings.getHoldings().size(), query);
    return new ResponseEntity<>(rtacHoldings, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<RtacHoldings> getRtacCacheHoldingsById(UUID instanceId, List<String> sort, Integer offset, Integer limit) {

    log.info("Received request to get RTAC holdings by instanceId: {}, offset: {}, limit: {}", instanceId, offset, limit);

    Page<RtacHolding> pagedRtacHoldings =
      rtacHoldingStorageService.getRtacHoldingsByInstanceId(instanceId.toString(), buildPageable(offset, limit, sort));

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

  @Override
  public ResponseEntity<RtacPreWarmingJob> postRtacCachePreWarmingJob(
    RtacSubmitPreWarming rtacSubmitPrewarmingRequest) {
    return ResponseEntity.ok(rtacCachePreWarmingService.submitPreWarmingJob(rtacSubmitPrewarmingRequest.getInstanceIds()));
  }

  @Override
  public ResponseEntity<RtacPreWarmingJob> getRtacCachePreWarmingJob(UUID id) {
    return ResponseEntity.ok(rtacCachePreWarmingService.getPreWarmingJobStatus(id));
  }

  @Override
  public ResponseEntity<RtacPreWarmingJobs> getRtacCachePreWarmingJobs(Integer offset, Integer limit) {
    var jobs = rtacCachePreWarmingService.getPreWarmingJobs(OffsetRequest.of(offset, limit));
    var response = new  RtacPreWarmingJobs();
    response.setJobs(jobs.getContent());
    response.setTotalRecords((int) jobs.getTotalElements());
    return ResponseEntity.ok(response);
  }

  private Pageable buildPageable(Integer offset, Integer limit, List<String> sort) {
    Sort sortOrder;
    if (sort == null || sort.isEmpty()) {
      sortOrder = Sort.by(Sort.Direction.ASC, "effectiveShelvingOrder", "libraryName", "locationName", "status");
    } else {
      List<Sort.Order> orders = new ArrayList<>();
      for (String sortParam : sort) {
        String[] parts = sortParam.split(",");
        Sort.Direction direction = parts.length > 1 ? Sort.Direction.fromString(parts[1]) : Sort.Direction.ASC;
        orders.add(new Sort.Order(direction, parts[0]));
      }
      sortOrder = Sort.by(orders);
    }
    return PageRequest.of(offset / limit, limit, sortOrder);
  }
}
