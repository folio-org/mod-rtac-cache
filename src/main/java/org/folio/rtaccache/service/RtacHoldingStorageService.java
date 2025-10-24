package org.folio.rtaccache.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RtacHoldingStorageService {

  private final RtacHoldingRepository rtacHoldingRepository;

  public Page<RtacHolding> getRtacHoldingsByInstanceId(String instanceId, int page, int size) {
    return rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(instanceId), PageRequest.of(page, size))
      .map(RtacHoldingEntity::getRtacHolding);
  }

}
