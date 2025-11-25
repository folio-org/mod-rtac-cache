package org.folio.rtaccache.service.handler;

import org.folio.rtaccache.domain.dto.PieceEventAction;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;

public interface PieceEventHandler extends EventHandler<PieceEventAction, PieceResourceEvent> {

  PieceEventAction getEventType();

  void handle(PieceResourceEvent resourceEvent);

}
