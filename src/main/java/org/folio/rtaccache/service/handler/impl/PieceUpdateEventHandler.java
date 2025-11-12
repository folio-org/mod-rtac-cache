package org.folio.rtaccache.service.handler.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
public class PieceUpdateEventHandler implements PieceEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository holdingRepository;

  @Override
  @Transactional
  public void handle(PieceResourceEvent resourceEvent) {
    holdingRepository.findByIdIdAndIdType(
        UUID.fromString(resourceEvent.getPieceId()), TypeEnum.PIECE)
      .ifPresent(existingPieceEntity -> {
        var pieceData = resourceEvent.getPieceSnapshot();
        var existingRtacHolding = existingPieceEntity.getRtacHolding();
        var updatedRtacHolding = rtacHoldingMappingService.mapForPieceTypeFrom(
          existingRtacHolding, pieceData);
        existingPieceEntity.setRtacHolding(updatedRtacHolding);
        holdingRepository.save(existingPieceEntity);
      });
  }

  @Override
  public PieceEventAction getEventType() {
    return PieceEventAction.EDIT;
  }

}
