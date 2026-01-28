package org.folio.rtaccache.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacBatchCountProjection;
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
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface RtacHoldingRepository extends JpaRepository<RtacHoldingEntity, RtacHoldingId>, RtacHoldingRepositoryCustom {

  Page<RtacHoldingEntity> findAllByIdInstanceId(UUID instanceId, Pageable pageable);

  @Query(value = """
      WITH FilteredHoldings AS (
          SELECT * FROM rtac_holdings_multi_tenant(:schemas, ARRAY[:instanceId], :onlyShared)
      )
      SELECT
          *,
          rtac_holding_json->>'effectiveShelvingOrder' AS effectiveShelvingOrder,
          rtac_holding_json->'library'->>'name' AS libraryName,
          rtac_holding_json->'location'->>'name' AS locationName,
          rtac_holding_json->>'status' AS status
      FROM FilteredHoldings
      """,
      countQuery = "SELECT count(*) FROM rtac_holdings_multi_tenant(:schemas, ARRAY[:instanceId], :onlyShared)",
      nativeQuery = true)
  Page<RtacHoldingEntity> findAllByIdInstanceId(@Param("schemas") String schemas, @Param("instanceId") UUID instanceId, @Param("onlyShared") boolean onlyShared, Pageable pageable);

  int countByIdInstanceId(UUID instanceId);

  @Query(value = "SELECT instance_id AS instanceId, count(*) AS count FROM rtac_holding WHERE instance_id in (:instanceIds) GROUP BY instance_id",
    nativeQuery = true)
  List<RtacBatchCountProjection> countBatchByIdInstanceIdIn(@Param("instanceIds") List<UUID> instanceIds);

  @Query(value = "SELECT count(*) FROM rtac_holdings_multi_tenant(:schemas, ARRAY[:instanceId], :onlyShared)", nativeQuery = true)
  int countByIdInstanceId(@Param("schemas") String schemas, @Param("instanceId") UUID instanceId, @Param("onlyShared") boolean onlyShared);

  Optional<RtacHoldingEntity> findByIdId(UUID id);

  Optional<RtacHoldingEntity> findByIdIdAndIdType(UUID id, RtacHolding.TypeEnum type);

  List<RtacHoldingEntity> findAllByIdInstanceIdAndIdType(UUID instanceId, RtacHolding.TypeEnum type);

  @Query(value = "SELECT * FROM rtac_holding WHERE rtac_holding_json->>'holdingsId' = :holdingsId", nativeQuery = true)
  List<RtacHoldingEntity> findAllByHoldingsId(@Param("holdingsId") String holdingsId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM rtac_holding WHERE rtac_holding_json->>'holdingsId' = :holdingsId", nativeQuery = true)
  void deleteAllByHoldingsId(@Param("holdingsId") String holdingsId);

  void deleteByIdId(UUID id);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  void deleteAllByIdInstanceId(UUID instanceId);

  @Query(value = """
        WITH Filtered AS (
          SELECT * FROM rtac_holdings_multi_tenant(:schemas, :instanceIds, :onlyShared)
        ),
        LocationStatusCounts AS (
          SELECT
            h_inner.instance_id,
            (h_inner.rtac_holding_json->'library'->>'id') AS libraryId,
            (h_inner.rtac_holding_json->'location'->>'id') AS locationId,
            (h_inner.rtac_holding_json->'location'->>'code') AS locationCode,
            (h_inner.rtac_holding_json->>'type') AS type,
            (h_inner.rtac_holding_json->>'status') AS status,
            COUNT(*) AS statusCount
          FROM
            Filtered h_inner
          GROUP BY
            h_inner.instance_id,
            (h_inner.rtac_holding_json->'library'->>'id'),
            (h_inner.rtac_holding_json->'location'->>'id'),
            (h_inner.rtac_holding_json->'location'->>'code'),
            (h_inner.rtac_holding_json->>'type'),
            (h_inner.rtac_holding_json->>'status')
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
                  'locationCode', lsc.locationCode,
                  'status', lsc.status,
                  'statusCount', lsc.statusCount,
                  'type', lsc.type
                )
              )
            FROM
              LocationStatusCounts lsc
            WHERE
              lsc.instance_id = h.instance_id
          ) AS locationStatusJson
        FROM
          Filtered h
        GROUP BY
          h.instance_id""",
      nativeQuery = true)
  List<RtacSummaryProjection> findRtacSummariesByInstanceIds(@Param("schemas") String schemas, @Param("instanceIds") UUID[] instanceIds, @Param("onlyShared") boolean onlyShared);

  @Query(value = "SELECT public.delete_old_holdings_all_tenants(:cutoffTime)", nativeQuery = true)
  int deleteOldHoldingsAllTenants(@Param("cutoffTime") Instant cutoffTime);

}
