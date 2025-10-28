package org.folio.rtaccache.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHoldingsSummary;
import org.folio.rtaccache.domain.dto.RtacHoldingsSummaryCopiesRemaining;
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

  public List<RtacHoldingsSummary> getRtacHoldingsSummaryForInstanceIds(List<UUID> instanceIds) {
    List<RtacSummaryProjection> projections = rtacHoldingRepository.findRtacSummariesByInstanceIds(instanceIds);

    Map<UUID, RtacSummaryProjection> summaryMap = projections.stream()
      .collect(Collectors.toMap(RtacSummaryProjection::instanceId, p -> p));

    return instanceIds.stream()
      .map(id -> {
        var summary = new RtacHoldingsSummary();
        summary.setInstanceId(id.toString());

        RtacSummaryProjection projection = summaryMap.get(id);
        var copiesRemaining = new RtacHoldingsSummaryCopiesRemaining();
        if (projection != null) {
          long totalCopies = projection.totalCopies();
          long availableCopies = projection.availableCopies();

          String status = availableCopies > 0 ? "Available" : "Unavailable";
          summary.setStatus(status);
          summary.setHasVolumes(projection.hasVolumes());

          copiesRemaining.total((int) totalCopies);
          copiesRemaining.available((int) availableCopies);
        } else {
          summary.setStatus("Unavailable");
          summary.setHasVolumes(false);
          copiesRemaining.total(0);
          copiesRemaining.available(0);
        }
        summary.setCopiesRemaining(copiesRemaining);
        return summary;
      }).toList();
  }
}
