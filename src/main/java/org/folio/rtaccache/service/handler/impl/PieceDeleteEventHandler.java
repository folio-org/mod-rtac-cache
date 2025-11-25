package org.folio.rtaccache.service.handler.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.PieceEventAction;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.PieceEventHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PieceDeleteEventHandler implements PieceEventHandler {

  private final RtacHoldingRepository holdingRepository;

  @Override
  @Transactional
  public void handle(PieceResourceEvent resourceEvent) {
    holdingRepository.findByIdId(UUID.fromString(resourceEvent.getPieceId())).ifPresent(
      existingPieceEntity ->
    holdingRepository.deleteByIdId(UUID.fromString(resourceEvent.getPieceId())));
  }

  @Override
  public PieceEventAction getEventType() {
    return PieceEventAction.DELETE;
  }

}
