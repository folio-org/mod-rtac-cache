package org.folio.rtaccache.repository;

import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RtacHoldingRepository extends JpaRepository<RtacHoldingEntity, RtacHoldingId> {

  Page<RtacHoldingEntity> findAllByIdInstanceId(UUID instanceId, Pageable pageable);
}
