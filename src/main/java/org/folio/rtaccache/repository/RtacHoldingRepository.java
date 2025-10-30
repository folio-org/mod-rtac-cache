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

  @Query(value = "WITH LocationStatusCounts AS ( " +
                 "SELECT " +
                 "h_inner.instance_id, " +
                 "(h_inner.rtac_holding_json->'library'->>'id') AS libraryId, " +
                 "(h_inner.rtac_holding_json->'library'->>'code') AS libraryCode, " +
                 "(h_inner.rtac_holding_json->'library'->>'name') AS libraryName, " +
                 "(h_inner.rtac_holding_json->'location'->>'id') AS locationId, " +
                 "(h_inner.rtac_holding_json->'location'->>'code') AS locationCode, " +
                 "(h_inner.rtac_holding_json->'location'->>'name') AS locationName, " +
                 "(h_inner.rtac_holding_json->>'status') AS status, " +
                 "COUNT(*) AS statusCount " +
                 "FROM " +
                 "rtac_holding h_inner " +
                 "WHERE " +
                 "h_inner.instance_id IN :instanceIds " +
                 "GROUP BY " +
                 "h_inner.instance_id, libraryId, libraryCode, libraryName, locationId, locationCode, locationName, status " +
                 ") " +
                 "SELECT " +
                 "h.instance_id AS instanceId, " +
                 "bool_or(h.rtac_holding_json->>'volume' IS NOT NULL AND h.rtac_holding_json->>'volume' != '') AS hasVolumes, " +
                 "( " +
                 "SELECT " +
                 "json_agg( " +
                 "json_build_object( " +
                 "'libraryId', lsc.libraryId, " +
                 "'libraryCode', lsc.libraryCode, " +
                 "'libraryName', lsc.libraryName, " +
                 "'locationId', lsc.locationId, " +
                 "'locationCode', lsc.locationCode, " +
                 "'locationName', lsc.locationName, " +
                 "'status', lsc.status, " +
                 "'statusCount', lsc.statusCount " +
                 ") " +
                 ") " +
                 "FROM " +
                 "LocationStatusCounts lsc " +
                 "WHERE " +
                 "lsc.instance_id = h.instance_id " +
                 ") AS locationAvailability " +
                 "FROM " +
                 "rtac_holding h " +
                 "WHERE " +
                 "h.instance_id IN :instanceIds " +
                 "GROUP BY " +
                 "h.instance_id",
         nativeQuery = true)
  List<RtacSummaryProjection> findRtacSummariesByInstanceIds(@Param("instanceIds") List<UUID> instanceIds);
}
