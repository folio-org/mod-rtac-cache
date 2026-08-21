package org.folio.rtaccache.service.handler;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.dto.CirculationEntityType;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.PieceEventAction;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class EventHandlerFactory {

  private final List<InventoryEventHandler> inventoryEventHandlers;
  private final List<CirculationEventHandler> circulationEventHandlers;
  private final List<PieceEventHandler> pieceEventHandlers;


  public Optional<InventoryEventHandler> getInventoryHandler(InventoryEventType eventType,
                                                              InventoryEntityType entityType) {
    if (eventType == InventoryEventType.UNKNOWN) {
      log.info("Skipping event with unsupported type for entity {}", entityType);
      return Optional.empty();
    }
    return inventoryEventHandlers.stream()
      .filter(handler -> handler.getEntityType().equals(entityType))
      .filter(handler -> handler.getEventType().equals(eventType))
      .findFirst();
  }

  public Optional<CirculationEventHandler> getCirculationHandler(CirculationEventType eventType,
                                                                 CirculationEntityType entityType) {
    if (eventType == CirculationEventType.UNKNOWN) {
      log.info("Skipping event with unsupported type for entity {}", entityType);
      return Optional.empty();
    }
    return circulationEventHandlers.stream()
      .filter(handler -> handler.getEntityType().equals(entityType))
      .filter(handler -> handler.getEventType().equals(eventType))
      .findFirst();
  }

  public Optional<PieceEventHandler> getPieceEventHandler(PieceEventAction eventType) {
    if (eventType == PieceEventAction.UNKNOWN) {
      log.warn("Skipping piece event with unsupported action {}", eventType);
      return Optional.empty();
    }
    return pieceEventHandlers.stream()
      .filter(handler -> handler.getEventType().equals(eventType))
      .findFirst();
  }

}
