package org.folio.rtaccache.service.handler.impl;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.PieceEventAction;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.service.handler.PieceEventHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PieceCreateEventHandler implements PieceEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository holdingRepository;

  @Override
  @Transactional
  public void handle(PieceResourceEvent resourceEvent) {
    var pieceData = resourceEvent.getPieceSnapshot();
    holdingRepository.findByIdIdAndIdType(
        UUID.fromString(pieceData.getHoldingId()), TypeEnum.HOLDING)
      .ifPresent(existingHoldingsEntity -> {
        var existingRtacHolding = existingHoldingsEntity.getRtacHolding();
        var newRtacHolding = rtacHoldingMappingService.mapForPieceTypeFrom(
          existingRtacHolding, pieceData);
        var newHoldingEntity = new RtacHoldingEntity();
        var rtacHoldingId = RtacHoldingId.from(newRtacHolding);
        newHoldingEntity.setId(rtacHoldingId);
        newHoldingEntity.setCreatedAt(Instant.now());
        newHoldingEntity.setRtacHolding(newRtacHolding);
        holdingRepository.save(newHoldingEntity);
      });
  }

  @Override
  public PieceEventAction getEventType() {
    return PieceEventAction.CREATE;
  }

}
