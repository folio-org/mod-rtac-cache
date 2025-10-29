package org.folio.rtaccache.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.RtacHolding;
import java.util.ArrayList;
import org.folio.rtaccache.domain.dto.Error;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacHoldingsSummary;
import org.folio.rtaccache.domain.dto.RtacHoldingsSummaryCopiesRemaining;
import org.springframework.http.HttpStatus;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.repository.RtacSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RtacHoldingStorageService {

  private final RtacHoldingRepository rtacHoldingRepository;

  public Page<RtacHolding> getRtacHoldingsByInstanceId(String instanceId, int page, int size) {
    return rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(instanceId), PageRequest.of(page, size))
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
        var copiesRemaining = new RtacHoldingsSummaryCopiesRemaining();
        long totalCopies = projection.totalCopies();
        long availableCopies = projection.availableCopies();

        String status = availableCopies > 0 ? "Available" : "Unavailable";
        summary.setStatus(status);
        summary.setHasVolumes(projection.hasVolumes());

        copiesRemaining.total((int) totalCopies);
        copiesRemaining.available((int) availableCopies);
        summary.setCopiesRemaining(copiesRemaining);
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
