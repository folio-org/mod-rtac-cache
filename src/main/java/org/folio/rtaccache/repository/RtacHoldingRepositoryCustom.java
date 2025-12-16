package org.folio.rtaccache.repository;

import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Interface for custom repository methods for RtacHoldingEntity.
 */
public interface RtacHoldingRepositoryCustom {

  /**
   * Performs a dynamic search for RtacHoldingEntity records.
   *
   * @param schemas A comma-separated string of database schemas to search across.
   * @param instanceId The instance ID to search within.
   * @param query A space-separated string of search terms.
   * @param available A flag to filter by availability status.
   * @param pageable Pagination information.
   * @return A page of matching RtacHoldingEntity records.
   */
  Page<RtacHoldingEntity> search(String schemas, UUID instanceId, String query, Boolean available, Pageable pageable);
}
