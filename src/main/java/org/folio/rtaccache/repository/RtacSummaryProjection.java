package org.folio.rtaccache.repository;

import java.util.List;
import java.util.UUID;

public record RtacSummaryProjection(
    UUID instanceId,
    boolean hasVolumes,
    String locationAvailabilityJson
) {
  public record LocationAvailability(
      String libraryId,
      String libraryCode,
      String libraryName,
      String locationId,
      String locationCode,
      String locationName,
      String status,
      Integer statusCount
  ) {}
}

