package org.folio.rtaccache.service.handler;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.CirculationEntityType;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.PieceEventAction;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventHandlerFactory {

  private final List<InventoryEventHandler> inventoryEventHandlers;
  private final List<CirculationEventHandler> circulationEventHandlers;
  private final List<PieceEventHandler> pieceEventHandlers;


  public Optional<InventoryEventHandler> getInventoryHandler(InventoryEventType eventType,
                                                              InventoryEntityType entityType) {
    return inventoryEventHandlers.stream()
      .filter(handler -> handler.getEntityType().equals(entityType))
      .filter(handler -> handler.getEventType().equals(eventType))
      .findFirst();
  }

  public Optional<CirculationEventHandler> getCirculationHandler(CirculationEventType eventType,
                                                                 CirculationEntityType entityType) {
    return circulationEventHandlers.stream()
      .filter(handler -> handler.getEntityType().equals(entityType))
      .filter(handler -> handler.getEventType().equals(eventType))
      .findFirst();
  }

  public Optional<PieceEventHandler> getPieceEventHandler(PieceEventAction eventType) {
    return pieceEventHandlers.stream()
      .filter(handler -> handler.getEventType().equals(eventType))
      .findFirst();
  }

}
