package org.folio.rtaccache.repository;

import java.util.UUID;
import org.folio.rtaccache.domain.RtacPreWarmingJobEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RtacPreWarmingJobRepository extends JpaRepository<RtacPreWarmingJobEntity, UUID> {

  Page<RtacPreWarmingJobEntity> findAllByOrderByStartDateDesc(Pageable pageable);

}
