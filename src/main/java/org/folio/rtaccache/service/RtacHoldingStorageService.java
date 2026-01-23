package org.folio.rtaccache.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.Error;
import org.folio.rtaccache.domain.dto.Parameter;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacHoldingsSummary;
import org.folio.rtaccache.domain.dto.StatusSummary;
import org.folio.rtaccache.domain.exception.RtacDataProcessingException;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.repository.RtacSummaryProjection;
import org.folio.rtaccache.util.EcsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RtacHoldingStorageService {

  private static final String INSTANCE_NOT_FOUND_MESSAGE = "Instance %s can not be retrieved";
  private static final Logger log = LoggerFactory.getLogger(RtacHoldingStorageService.class);

  private final RtacHoldingRepository rtacHoldingRepository;
  private final ConsortiaService consortiaService;
  private final RtacHoldingLazyLoadingService rtacHoldingLazyLoadingService;
  private final ObjectMapper objectMapper;
  private final EcsUtil ecsUtil;

  public Page<RtacHolding> searchRtacHoldings(UUID instanceId, String query, Boolean available, Pageable pageable) {
    var isCentral = consortiaService.isCentralTenant();
    var schema = ecsUtil.getSchemaName();
    var lazyLoadExceptions = lazyLoadInstances(List.of(instanceId), isCentral);
    if (!lazyLoadExceptions.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to lazy load RTAC holdings for instanceId: " + instanceId, lazyLoadExceptions.get(0));
    }
    return rtacHoldingRepository.search(schema, instanceId, query, available, isCentral, pageable)
      .map(RtacHoldingEntity::getRtacHolding);
  }

  public Page<RtacHolding> getRtacHoldingsByInstanceId(UUID instanceId, Pageable pageable) {
    var isCentral = consortiaService.isCentralTenant();
    var schema = ecsUtil.getSchemaName();
    var lazyLoadExceptions = lazyLoadInstances(List.of(instanceId), isCentral);
    if (!lazyLoadExceptions.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to lazy load RTAC holdings for instanceId: " + instanceId, lazyLoadExceptions.get(0));
    }
    return rtacHoldingRepository.findAllByIdInstanceId(schema, instanceId, isCentral, pageable)
      .map(RtacHoldingEntity::getRtacHolding);
  }

  public RtacHoldingsBatch getRtacHoldingsSummaryForInstanceIds(List<UUID> instanceIds) {
    var isCentral = consortiaService.isCentralTenant();
    var schema = ecsUtil.getSchemaName();
    var lazyLoadExceptions = lazyLoadInstances(instanceIds, isCentral);
    List<RtacSummaryProjection> projections = rtacHoldingRepository.findRtacSummariesByInstanceIds(schema, instanceIds.toArray(new UUID[0]), isCentral);

    Map<UUID, RtacSummaryProjection> summaryMap = projections.stream()
      .collect(Collectors.toMap(RtacSummaryProjection::instanceId, p -> p));

    final var result = new RtacHoldingsBatch();
    final var holdings = new ArrayList<RtacHoldingsSummary>();
    final var errors = new ArrayList<Error>();

    instanceIds.forEach(id -> {
      RtacSummaryProjection projection = summaryMap.get(id);
      if (projection != null) {
        var summary = new RtacHoldingsSummary();
        summary.setInstanceId(id.toString());
        summary.setHasVolumes(projection.hasVolumes());
        try {
          List<StatusSummary> statusSummaries = objectMapper.readValue(projection.statusSummariesJson(), new TypeReference<>() {});
          summary.setStatusSummaries(statusSummaries);
        } catch (JsonProcessingException e) {
          throw new RtacDataProcessingException(String.format("Failed to parse locationStatusJson for instanceId %s", id), e);
        }
        holdings.add(summary);
      } else {
        var parameter = new Parameter();
        parameter.setKey("instanceId");
        parameter.setValue(id.toString());
        var error = new Error();
        error.setCode(String.valueOf(HttpStatus.NOT_FOUND.value()));
        error.setMessage(String.format(INSTANCE_NOT_FOUND_MESSAGE, id));
        error.setParameters(List.of(parameter));
        errors.add(error);
        log.warn("Instance ID not found: {}", id);
      }
    });

    for (Throwable exception : lazyLoadExceptions) {
      var error = new Error();
      error.setCode(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
      error.setMessage(exception.getMessage());
      errors.add(error);
    }

    result.setHoldings(holdings);
    result.setErrors(errors);
    return result;
  }

  @Transactional
  public void deleteByInstanceIds(List<UUID> instanceIds) {
    log.debug("Deleting RTAC cache entries for instance IDs: {}", instanceIds);
    rtacHoldingRepository.deleteAllByIdInstanceIdIn(instanceIds);
  }

  @Transactional
  public void deleteAllCacheEntries() {
    log.info("Deleting all RTAC cache entries");
    rtacHoldingRepository.deleteAll();
  }

  private List<Throwable> lazyLoadInstances(List<UUID> instanceIds, boolean isCentral) {
    if (isCentral) {
      return rtacHoldingLazyLoadingService.lazyLoadRtacHoldingsEcs(instanceIds);
    } else {
      return rtacHoldingLazyLoadingService.lazyLoadRtacHoldings(instanceIds);
    }
  }

}
