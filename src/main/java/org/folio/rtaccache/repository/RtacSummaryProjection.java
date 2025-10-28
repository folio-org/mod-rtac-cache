package org.folio.rtaccache.repository;

import java.util.UUID;

public record RtacSummaryProjection(
    UUID instanceId,
    long totalCopies,
    long availableCopies,
    boolean hasVolumes
) {}
