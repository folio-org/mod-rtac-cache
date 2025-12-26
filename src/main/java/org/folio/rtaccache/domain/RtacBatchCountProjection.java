package org.folio.rtaccache.domain;

import java.util.UUID;

public record RtacBatchCountProjection (UUID instanceId, long count) {}
