package org.folio.rtaccache.repository;

import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RtacHoldingRepository extends JpaRepository<RtacHoldingEntity, RtacHoldingId> {

  Page<RtacHoldingEntity> findAllByIdInstanceId(UUID instanceId, Pageable pageable);

  @Query(value = "SELECT " +
                 "h.instance_id AS instanceId, " +
                 "COUNT(h.instance_id) AS totalCopies, " +
                 "SUM(CASE WHEN (h.rtac_holding_json->>'status') = 'Available' THEN 1 ELSE 0 END) AS availableCopies, " +
                 "bool_or(h.rtac_holding_json->>'volume' IS NOT NULL AND h.rtac_holding_json->>'volume' != '') AS hasVolumes " +
                 "FROM rtac_holding h " +
                 "WHERE h.instance_id IN :instanceIds " +
                 "GROUP BY h.instance_id",
         nativeQuery = true)
  List<RtacSummaryProjection> findRtacSummariesByInstanceIds(@Param("instanceIds") List<UUID> instanceIds);
}
