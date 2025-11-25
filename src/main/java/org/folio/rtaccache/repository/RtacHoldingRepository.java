package org.folio.rtaccache.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RtacHoldingRepository extends JpaRepository<RtacHoldingEntity, RtacHoldingId> {

  @Query(value = """
      SELECT * FROM rtac_holding h
      WHERE
      h.instance_id = :instanceId AND
      (:query is null OR
        (SELECT bool_and(exists) FROM (
          SELECT (
            cast(h.rtac_holding_json ->> 'volume' as text) ILIKE '%' || term || '%' OR
            cast(h.rtac_holding_json ->> 'callNumber' as text) ILIKE '%' || term || '%' OR
            cast(h.rtac_holding_json -> 'location' ->> 'name' as text) ILIKE '%' || term || '%' OR
            cast(h.rtac_holding_json -> 'library' ->> 'name' as text) ILIKE '%' || term || '%'
          ) as exists
          FROM unnest(string_to_array(:query, ' ')) as term
        ) as terms)
      )
      AND (:available is null OR cast(h.rtac_holding_json ->> 'status' as text) = 'Available')
      """, nativeQuery = true)
  Page<RtacHoldingEntity> search(@Param("instanceId") UUID instanceId, @Param("query") String query, @Param("available") Boolean available, Pageable pageable);

  Page<RtacHoldingEntity> findAllByIdInstanceId(UUID instanceId, Pageable pageable);

  int countByIdInstanceId(UUID instanceId);

  Optional<RtacHoldingEntity> findByIdId(UUID id);

  Optional<RtacHoldingEntity> findByIdIdAndIdType(UUID id, RtacHolding.TypeEnum type);

  @Query(value = "SELECT * FROM rtac_holding WHERE rtac_holding_json->>'holdingsId' = :holdingsId", nativeQuery = true)
  List<RtacHoldingEntity> findAllByHoldingsId(@Param("holdingsId") String holdingsId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM rtac_holding WHERE rtac_holding_json->>'holdingsId' = :holdingsId", nativeQuery = true)
  void deleteAllByHoldingsId(@Param("holdingsId") String holdingsId);

  void deleteByIdId(UUID id);

  void deleteAllByIdInstanceId(UUID instanceId);

  @Query(value = """
                 WITH LocationStatusCounts AS (
                 SELECT
                 h_inner.instance_id,
                 (h_inner.rtac_holding_json->'library'->>'id') AS libraryId,
                 (h_inner.rtac_holding_json->'location'->>'id') AS locationId,
                 (h_inner.rtac_holding_json->>'status') AS status,
                 COUNT(*) AS statusCount
                 FROM
                 rtac_holding h_inner
                 WHERE
                 h_inner.instance_id IN :instanceIds
                 GROUP BY
                 h_inner.instance_id, libraryId, locationId, status
                 )
                 SELECT
                 h.instance_id AS instanceId,
                 bool_or(h.rtac_holding_json->>'volume' IS NOT NULL AND h.rtac_holding_json->>'volume' != '') AS hasVolumes,
                 (
                 SELECT
                 json_agg(
                 json_build_object(
                 'libraryId', lsc.libraryId,
                 'locationId', lsc.locationId,
                 'status', lsc.status,
                 'statusCount', lsc.statusCount
                 )
                 )
                 FROM
                 LocationStatusCounts lsc
                 WHERE
                 lsc.instance_id = h.instance_id
                 ) AS locationStatusJson
                 FROM
                 rtac_holding h
                 WHERE
                 h.instance_id IN :instanceIds
                 GROUP BY
                 h.instance_id""",
         nativeQuery = true)
  List<RtacSummaryProjection> findRtacSummariesByInstanceIds(@Param("instanceIds") List<UUID> instanceIds);
}
