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

  Page<RtacHoldingEntity> findAllByIdInstanceId(UUID instanceId, Pageable pageable);

  Optional<RtacHoldingEntity> findByIdId(UUID id);

  Optional<RtacHoldingEntity> findByIdIdAndIdType(UUID id, RtacHolding.TypeEnum type);

  @Query(value = "SELECT * FROM rtac_holding WHERE rtac_holding_json->>'holdingsId' = :holdingsId", nativeQuery = true)
  List<RtacHoldingEntity> findAllByHoldingsId(@Param("holdingsId") String holdingsId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM rtac_holding WHERE rtac_holding_json->>'holdingsId' = :holdingsId", nativeQuery = true)
  void deleteAllByHoldingsId(@Param("holdingsId") String holdingsId);

  void deleteByIdId(UUID id);

}
