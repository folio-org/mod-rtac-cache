package org.folio.rtaccache.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.RtacBatchCountProjection;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Component;

/**
 * The database calls made by lazy loading and cache generation, behind one shared concurrency limit.
 *
 * <p>Lazy loading fans out into an unbounded number of virtual-thread tasks that each need a pooled
 * connection; without a limit they queue on, and time out waiting for, the Hikari pool. The
 * class-level {@link ConcurrencyLimit} gives all methods here a single shared limit. It is held
 * only for one leaf database call, never while waiting on other tasks, so parent tasks that join
 * child tasks cannot deadlock on it. Keep the limit below the pool size so other callers, such as
 * the Kafka handlers and REST reads, still get connections.
 */
@Component
@RequiredArgsConstructor
@ConcurrencyLimit(limitString = "${rtac.cache.db-max-concurrency}")
public class LazyLoadDbOperations {

  private final RtacHoldingRepository rtacHoldingRepository;
  private final RtacHoldingBulkRepository rtacHoldingBulkRepository;

  public int countByIdInstanceId(UUID instanceId) {
    return rtacHoldingRepository.countByIdInstanceId(instanceId);
  }

  public List<RtacBatchCountProjection> countBatchByIdInstanceIdIn(List<UUID> instanceIds) {
    return rtacHoldingRepository.countBatchByIdInstanceIdIn(instanceIds);
  }

  public void deleteAllByIdInstanceId(UUID instanceId) {
    rtacHoldingRepository.deleteAllByIdInstanceId(instanceId);
  }

  public void bulkUpsert(List<RtacHoldingEntity> holdings) throws SQLException {
    rtacHoldingBulkRepository.bulkUpsert(holdings);
  }
}
