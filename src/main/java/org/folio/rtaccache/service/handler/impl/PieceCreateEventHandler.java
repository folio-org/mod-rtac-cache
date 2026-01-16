package org.folio.rtaccache.service.handler.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PieceCreateEventHandler implements PieceEventHandler {

  private final RtacHoldingMappingService rtacHoldingMappingService;
  private final RtacHoldingRepository holdingRepository;
  private final FolioExecutionContext folioExecutionContext;
  private final ConsortiaService consortiaService;
  private final SystemUserScopedExecutionService systemUserExecutionService;

  @Override
  public void handle(PieceResourceEvent resourceEvent) {
    log.info("Handling piece create event: {}", resourceEvent);
    var pieceData = resourceEvent.getPieceSnapshot();
    if (pieceData == null || pieceData.getHoldingId() == null) {
      return;
    }
    var eventTenant = folioExecutionContext.getTenantId();
    log.info("Event tenant id: {}", eventTenant);
    var holdingsTenant = eventTenant;
    if (consortiaService.isCentralTenant()) {
      log.info("Current tenant is central tenant, using receiving tenant id from piece data");
      holdingsTenant = pieceData.getReceivingTenantId();
    }
    var newHoldingEntity = systemUserExecutionService.executeSystemUserScoped(holdingsTenant, () -> {
        log.info("current tenant id for holdings retrieval: {}", folioExecutionContext.getTenantId());
        return getRtacHoldingFromPieceEvent(pieceData);
      }
    );
    log.info("Obtained new RTAC holding entity for piece id: {}: {}", pieceData.getId(), newHoldingEntity.isPresent());
    systemUserExecutionService.executeSystemUserScoped(eventTenant, () -> {
      log.info("Saving new RTAC holding entity for piece id: {} in tenant: {}", pieceData.getId(), eventTenant);
      newHoldingEntity.ifPresent(holdingRepository::save);
      return null;
    });
  }

  private Optional<RtacHoldingEntity> getRtacHoldingFromPieceEvent(Piece pieceData) {
    return holdingRepository.findByIdIdAndIdType(
        UUID.fromString(pieceData.getHoldingId()), TypeEnum.HOLDING)
      .map(existingHoldingsEntity -> {
        log.info("Found existing RTAC holding entity for holdings id: {}", existingHoldingsEntity.getId());
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
