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
import org.folio.rtaccache.domain.dto.LocationStatus;
import org.folio.rtaccache.domain.dto.Parameter;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacHoldingsSummary;
import org.folio.rtaccache.domain.exception.RtacDataProcessingException;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.repository.RtacSummaryProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RtacHoldingStorageService {

  private static final String INSTANCE_NOT_FOUND_MESSAGE = "Instance %s can not be retrieved";
  private static final Logger log = LoggerFactory.getLogger(RtacHoldingStorageService.class);

  private final RtacHoldingRepository rtacHoldingRepository;
  private final RtacCacheGenerationService rtacCacheGenerationService;
  private final ObjectMapper objectMapper;

  public Page<RtacHolding> searchRtacHoldings(UUID instanceId, String query, Boolean available, Pageable pageable) {
    return rtacHoldingRepository.search(instanceId, query, available, pageable)
      .map(RtacHoldingEntity::getRtacHolding);
  }

  public Page<RtacHolding> getRtacHoldingsByInstanceId(String instanceId, Pageable pageable) {
    if (rtacHoldingRepository.countByIdInstanceId(UUID.fromString(instanceId)) == 0) {
      var future = rtacCacheGenerationService.generateRtacCache(instanceId);
      try {
        future.join();
      } catch (Exception ex) {
        log.error("RTAC cache generation failed for instanceId: {}", instanceId, ex);
        rtacHoldingRepository.deleteAllByIdInstanceId(UUID.fromString(instanceId));
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          String.format("RTAC cache generation failed for instanceId: %s", instanceId));
      }
    }
    return rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(instanceId), pageable)
      .map(RtacHoldingEntity::getRtacHolding);
  }

  public RtacHoldingsBatch getRtacHoldingsSummaryForInstanceIds(List<UUID> instanceIds) {
    List<RtacSummaryProjection> projections = rtacHoldingRepository.findRtacSummariesByInstanceIds(instanceIds);

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
          List<LocationStatus> locationStatuses = objectMapper.readValue(projection.locationStatusJson(), new TypeReference<>() {});
          summary.setLocationStatus(locationStatuses);
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

    result.setHoldings(holdings);
    result.setErrors(errors);
    return result;
  }
}
