package org.folio.rtaccache.service.handler.impl;

import java.time.Instant;
import java.util.Optional;
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
public class PieceUpdateEventHandler implements PieceEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository holdingRepository;

  @Override
  @Transactional
  public void handle(PieceResourceEvent resourceEvent) {
    if (resourceEvent.getPieceSnapshot() == null || resourceEvent.getPieceId() == null) {
      return;
    }
    if (isPublicPiece(resourceEvent)) {
      var entityToSave = getUpdatedExistingPiece(resourceEvent)
        .orElseGet(() -> createNewPieceFromHolding(resourceEvent).orElse(null));
      if (entityToSave != null) {
        holdingRepository.save(entityToSave);
      }
    } else {
      holdingRepository.deleteByIdId(UUID.fromString(resourceEvent.getPieceId()));
    }
  }

  private Optional<RtacHoldingEntity> getUpdatedExistingPiece(PieceResourceEvent resourceEvent) {
    return holdingRepository.findByIdIdAndIdType(
      UUID.fromString(resourceEvent.getPieceId()), TypeEnum.PIECE).map(existingPieceEntity -> {
      var pieceData = resourceEvent.getPieceSnapshot();
      var existingRtacHolding = existingPieceEntity.getRtacHolding();
      var updatedRtacHolding = rtacHoldingMappingService.mapForPieceTypeFrom(
        existingRtacHolding, pieceData);
      existingPieceEntity.setRtacHolding(updatedRtacHolding);
      return existingPieceEntity;
    });
  }

  private Optional<RtacHoldingEntity> createNewPieceFromHolding(PieceResourceEvent resourceEvent) {
    var pieceData = resourceEvent.getPieceSnapshot();
    return holdingRepository.findByIdIdAndIdType(
        UUID.fromString(pieceData.getHoldingId()), TypeEnum.HOLDING)
      .map(existingEntity -> {
        var newRtacHolding = rtacHoldingMappingService.mapForPieceTypeFrom(
          existingEntity.getRtacHolding(), pieceData);
        var newEntity = new RtacHoldingEntity();
        newEntity.setId(RtacHoldingId.from(newRtacHolding));
        newEntity.setShared(existingEntity.isShared());
        newEntity.setCreatedAt(Instant.now());
        newEntity.setRtacHolding(newRtacHolding);
        return newEntity;
      });
  }

  @Override
  public PieceEventAction getEventType() {
    return PieceEventAction.EDIT;
  }

}
