package org.folio.rtaccache.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.sql.RtacQueryFragments;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RtacHoldingRepository extends JpaRepository<RtacHoldingEntity, RtacHoldingId>, RtacHoldingRepositoryCustom {

  Page<RtacHoldingEntity> findAllByIdInstanceId(UUID instanceId, Pageable pageable);

  @Query(value = """
      WITH FilteredHoldings AS (
          SELECT * FROM rtac_holdings_multi_tenant(:schemas, ARRAY[:instanceId])
      )
      SELECT
          *
      """ + RtacQueryFragments.SORT_COLUMN_PROJECTIONS + """
      FROM FilteredHoldings
      """,
      countQuery = "SELECT count(*) FROM rtac_holdings_multi_tenant(:schemas, ARRAY[:instanceId])",
      nativeQuery = true)
  Page<RtacHoldingEntity> findAllByIdInstanceId(@Param("schemas") String schemas, @Param("instanceId") UUID instanceId, Pageable pageable);

  int countByIdInstanceId(UUID instanceId);

  @Query(value = "SELECT count(*) FROM rtac_holdings_multi_tenant(:schemas, ARRAY[:instanceId])", nativeQuery = true)
  int countByIdInstanceId(@Param("schemas") String schemas, @Param("instanceId") UUID instanceId);

  Optional<RtacHoldingEntity> findByIdId(UUID id);

  Optional<RtacHoldingEntity> findByIdIdAndIdType(UUID id, RtacHolding.TypeEnum type);

  @Query(value = "SELECT * FROM rtac_holding WHERE rtac_holding_json->>'holdingsId' = :holdingsId", nativeQuery = true)
  List<RtacHoldingEntity> findAllByHoldingsId(@Param("holdingsId") String holdingsId);

  @Query(value = "SELECT * FROM rtac_holding WHERE rtac_holding_json->'location'->>'id' = :locationId", nativeQuery = true)
  List<RtacHoldingEntity> findAllByLocationId(@Param("locationId") String locationId);

  @Query(value = "SELECT * FROM rtac_holding WHERE rtac_holding_json->'library'->>'id' = :libraryId", nativeQuery = true)
  List<RtacHoldingEntity> findAllByLibraryId(@Param("libraryId") String libraryId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM rtac_holding WHERE rtac_holding_json->>'holdingsId' = :holdingsId", nativeQuery = true)
  void deleteAllByHoldingsId(@Param("holdingsId") String holdingsId);

  void deleteByIdId(UUID id);

  void deleteAllByIdInstanceId(UUID instanceId);

  @Query(value = """
        WITH Filtered AS (
          SELECT * FROM rtac_holdings_multi_tenant(:schemas, :instanceIds)
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
  List<RtacSummaryProjection> findRtacSummariesByInstanceIds(@Param("schemas") String schemas, @Param("instanceIds") UUID[] instanceIds);

  @Modifying
  @Query(value = """
  UPDATE rtac_holding_entity
  SET rtac_holding_json = jsonb_set(
      jsonb_set(
          rtac_holding_json,
          '{location,name}',
          to_jsonb(:name::text)
      ),
      '{location,code}',
      to_jsonb(:code::text)
  )
  WHERE id IN (
    SELECT id FROM rtac_holding_entity
    WHERE rtac_holding_json->'location'->>'id' = :locationId
  )
  """, nativeQuery = true)
  int updateLocationDataBatch(@Param("locationId") String locationId,
    @Param("name") String name,
    @Param("code") String code);
}
