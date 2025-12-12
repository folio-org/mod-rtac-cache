package org.folio.rtaccache.service.handler;

import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;

public interface InventoryEventHandler extends EventHandler<InventoryEventType, InventoryResourceEvent> {

  void handle(InventoryResourceEvent resourceEvent);

  InventoryEventType getEventType();

  InventoryEntityType getEntityType();

}
