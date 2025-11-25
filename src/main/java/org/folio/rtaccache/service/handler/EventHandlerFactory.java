package org.folio.rtaccache.service.handler;

import java.util.List;
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


  public InventoryEventHandler getInventoryHandler(InventoryEventType eventType, InventoryEntityType entityType) {
    return inventoryEventHandlers.stream()
      .filter(handler -> handler.getEntityType().equals(entityType))
      .filter(handler -> handler.getEventType().equals(eventType))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No InventoryEventHandler with type " + entityType + " found"));
  }

  public CirculationEventHandler getCirculationHandler(CirculationEventType eventType, CirculationEntityType entityType) {
    return circulationEventHandlers.stream()
      .filter(handler -> handler.getEntityType().equals(entityType))
      .filter(handler -> handler.getEventType().equals(eventType))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No CirculationEventHandler with type " + entityType + " found"));
  }

  public PieceEventHandler getPieceEventHandler(PieceEventAction eventType) {
    return pieceEventHandlers.stream()
      .filter(handler -> handler.getEventType().equals(eventType))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No PieceEventHandler found for event action: " + eventType));
  }

}
