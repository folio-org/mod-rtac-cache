package org.folio.rtaccache.service.handler.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.Piece;
import org.folio.rtaccache.domain.dto.PieceEventAction;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.ConsortiaService;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.folio.rtaccache.service.handler.PieceEventHandler;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PieceCreateEventHandler implements PieceEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository holdingRepository;
  private final FolioExecutionContext folioExecutionContext;
  private final ConsortiaService consortiaService;
  private final SystemUserScopedExecutionService systemUserExecutionService;

  @Override
  @Transactional
  public void handle(PieceResourceEvent resourceEvent) {
    var pieceData = resourceEvent.getPieceSnapshot();
    if (pieceData == null || pieceData.getHoldingId() == null) {
      return;
    }
    var eventTenant = folioExecutionContext.getTenantId();
    var holdingsTenant = eventTenant;
    if (consortiaService.isCentralTenant()) {
      holdingsTenant = pieceData.getReceivingTenantId();
    }
    var newHoldingEntity = systemUserExecutionService.executeSystemUserScoped(holdingsTenant, () ->
      getRtacHoldingFromPieceEvent(pieceData)
    );
    systemUserExecutionService.executeSystemUserScoped(eventTenant, () -> {
      newHoldingEntity.ifPresent(holdingRepository::save);
      return null;
    });
  }

  private Optional<RtacHoldingEntity> getRtacHoldingFromPieceEvent(Piece pieceData) {
    return holdingRepository.findByIdIdAndIdType(
        UUID.fromString(pieceData.getHoldingId()), TypeEnum.HOLDING)
      .map(existingHoldingsEntity -> {
        var existingRtacHolding = existingHoldingsEntity.getRtacHolding();
        var newRtacHolding = rtacHoldingMappingService.mapForPieceTypeFrom(
          existingRtacHolding, pieceData);
        var newHoldingEntity = new RtacHoldingEntity();
        var rtacHoldingId = RtacHoldingId.from(newRtacHolding);
        newHoldingEntity.setId(rtacHoldingId);
        newHoldingEntity.setShared(existingHoldingsEntity.isShared());
        newHoldingEntity.setCreatedAt(Instant.now());
        newHoldingEntity.setRtacHolding(newRtacHolding);
        return newHoldingEntity;
      });
  }

  @Override
  public PieceEventAction getEventType() {
    return PieceEventAction.CREATE;
  }

}
