package org.folio.rtaccache.service;

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
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacHoldingsSummary;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.repository.RtacSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RtacHoldingStorageService {

  private final RtacHoldingRepository rtacHoldingRepository;
  private final ObjectMapper objectMapper;

  public Page<RtacHolding> getRtacHoldingsByInstanceId(String instanceId, Pageable pageable) {
    return rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(instanceId), pageable)
      .map(RtacHoldingEntity::getRtacHolding);
  }

  private static final String INSTANCE_NOT_FOUND_MESSAGE = "Instance %s can not be retrieved";

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
          List<LocationStatus> locationStatuses = objectMapper.readValue(
            projection.locationStatusJson(), new TypeReference<List<LocationStatus>>() {});
          summary.setLocationStatus(locationStatuses);
        } catch (Exception e) {
          // Handle JSON parsing error, e.g., log it or add an error to the batch
          System.err.println("Error parsing locationStatusJson: " + e.getMessage());
        }
        holdings.add(summary);
      } else {
        var error = new Error();
        error.setCode(String.valueOf(HttpStatus.NOT_FOUND.value()));
        error.setMessage(String.format(INSTANCE_NOT_FOUND_MESSAGE, id));
        errors.add(error);
      }
    });
    result.setHoldings(holdings);
    result.setErrors(errors);
    return result;
  }
}
