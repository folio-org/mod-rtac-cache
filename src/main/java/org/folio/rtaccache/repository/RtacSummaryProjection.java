package org.folio.rtaccache.repository;

import java.util.UUID;

public record RtacSummaryProjection(
    UUID instanceId,
    boolean hasVolumes,
    String statusSummariesJson
) {
  public record StatusSummary(
      String libraryId,
      String locationId,
      String status,
      Integer statusCount
  ) {}
}

